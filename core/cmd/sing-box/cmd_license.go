package main

import (
	"context"
	"os"
	"sync"

	"github.com/sagernet/sing-box/common/licensing"
	"github.com/sagernet/sing-box/log"

	"github.com/spf13/cobra"
)

var (
	licenseServerURL string

	licenseManagerOnce sync.Once
	licenseManagerInst *licensing.Manager
)

// licenseManager returns the process-wide Manager. The cache path is relative, so it
// lands inside --directory, which the root command has already chdir'd into.
func licenseManager() *licensing.Manager {
	licenseManagerOnce.Do(func() {
		licenseManagerInst = licensing.NewManager(
			licensing.NewEnterpriseProvider(licenseServerURL),
			licensing.DefaultCachePath(""),
		)
	})
	return licenseManagerInst
}

// gateLicense is the connect gate for the service commands. It returns an error only
// when licensing.Enforce is set and the licence is not valid.
func gateLicense(ctx context.Context) error {
	result, err := licenseManager().Gate(ctx)
	if err != nil {
		return err
	}
	if !result.Valid() {
		// Not fatal while Enforce is false, but the operator should still see it.
		log.Warn("license not valid (", result.Status, "): ", result.DenialMessage(),
			"; continuing because enforcement is disabled")
	}
	return nil
}

var commandLicense = &cobra.Command{
	Use:   "license",
	Short: "Manage the client license",
}

var commandLicenseStatus = &cobra.Command{
	Use:   "status",
	Short: "Show the current license status",
	Args:  cobra.NoArgs,
	Run: func(cmd *cobra.Command, args []string) {
		result := licenseManager().Verify(cmd.Context())
		os.Stdout.WriteString("status:  " + string(result.Status) + "\n")
		if result.LicenseID != "" {
			os.Stdout.WriteString("license: " + result.LicenseID + "\n")
		}
		if !result.ExpiresAt.IsZero() {
			os.Stdout.WriteString("expires: " + result.ExpiresAt.Format("2006-01-02 15:04:05Z07:00") + "\n")
		}
		if message := result.DenialMessage(); message != "" {
			os.Stdout.WriteString("detail:  " + message + "\n")
		}
		if !result.Valid() {
			os.Exit(1)
		}
	},
}

var commandLicenseActivate = &cobra.Command{
	Use:   "activate <license-key>",
	Short: "Activate this device",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		result := licenseManager().Activate(cmd.Context(), args[0])
		if !result.Valid() {
			log.Fatal("activation failed (", result.Status, "): ", result.DenialMessage())
		}
		os.Stdout.WriteString("activated: " + result.LicenseID + "\n")
	},
}

var commandLicenseDeactivate = &cobra.Command{
	Use:   "deactivate",
	Short: "Release this device's license seat",
	Args:  cobra.NoArgs,
	Run: func(cmd *cobra.Command, args []string) {
		if err := licenseManager().Deactivate(cmd.Context()); err != nil {
			log.Fatal(err)
		}
		os.Stdout.WriteString("deactivated\n")
	},
}

func init() {
	commandLicense.PersistentFlags().StringVar(&licenseServerURL, "license-server", "",
		"licensing server base URL (default $SINGBOX_LICENSE_URL)")
	commandLicense.AddCommand(commandLicenseStatus)
	commandLicense.AddCommand(commandLicenseActivate)
	commandLicense.AddCommand(commandLicenseDeactivate)
	mainCommand.AddCommand(commandLicense)
}
