package main

import (
	"io"
	"os"

	"github.com/sagernet/sing-box/common/configcodec"
	"github.com/sagernet/sing-box/log"

	"github.com/spf13/cobra"
)

var commandEncode = &cobra.Command{
	Use:   "encode [config.json]",
	Short: "Encode a config file for distribution",
	Long: `Encode a JSON config file using the built-in codec.
Output can be distributed to clients who will auto-decode it on import.

Examples:
  sing-box encode config.json > config.enc
  cat config.json | sing-box encode - > config.enc`,
	Args: cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		err := encode(args)
		if err != nil {
			log.Fatal(err)
		}
	},
}

var commandDecode = &cobra.Command{
	Use:   "decode [config.enc]",
	Short: "Decode an encoded config file",
	Long: `Decode an encoded config file back to plaintext JSON.
Useful for debugging or verifying encoded configs.

Examples:
  sing-box decode config.enc
  cat config.enc | sing-box decode -`,
	Args: cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		err := decode(args)
		if err != nil {
			log.Fatal(err)
		}
	},
}

func init() {
	mainCommand.AddCommand(commandEncode)
	mainCommand.AddCommand(commandDecode)
}

func encode(args []string) error {
	var content []byte
	var err error

	if len(args) == 0 || args[0] == "-" {
		content, err = io.ReadAll(os.Stdin)
	} else {
		content, err = os.ReadFile(args[0])
	}
	if err != nil {
		return err
	}

	encoded := configcodec.Encode(string(content))
	_, err = os.Stdout.WriteString(encoded)
	return err
}

func decode(args []string) error {
	var content []byte
	var err error

	if len(args) == 0 || args[0] == "-" {
		content, err = io.ReadAll(os.Stdin)
	} else {
		content, err = os.ReadFile(args[0])
	}
	if err != nil {
		return err
	}

	decoded := configcodec.Decode(string(content))
	_, err = os.Stdout.WriteString(decoded)
	return err
}
