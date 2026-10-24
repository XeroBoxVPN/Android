package build_shared

import (
	"os"

	"github.com/sagernet/sing-box/common/badversion"
	"github.com/sagernet/sing/common"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/shell"
)

func ReadTag() (string, error) {
	// XeroBox: core/ lives inside the app repository, whose tags are the app's, so the build
	// passes the core version explicitly (version.properties CORE_VERSION).
	if version := os.Getenv("XEROBOX_CORE_VERSION"); version != "" {
		return version, nil
	}
	currentTag, err := shell.Exec("git", "describe", "--tags").ReadOutput()
	if err != nil {
		return currentTag, err
	}
	currentTagRev, _ := shell.Exec("git", "describe", "--tags", "--abbrev=0").ReadOutput()
	if currentTagRev == currentTag {
		return currentTag[1:], nil
	}
	shortCommit, _ := shell.Exec("git", "rev-parse", "--short", "HEAD").ReadOutput()
	version := badversion.Parse(currentTagRev[1:])
	return version.String() + "-" + shortCommit, nil
}

func ReadTagVersionRev() (badversion.Version, error) {
	currentTagRev := common.Must1(shell.Exec("git", "describe", "--tags", "--abbrev=0").ReadOutput())
	return badversion.Parse(currentTagRev[1:]), nil
}

func ReadTagVersion() (badversion.Version, error) {
	currentTag := common.Must1(shell.Exec("git", "describe", "--tags").ReadOutput())
	currentTagRev := common.Must1(shell.Exec("git", "describe", "--tags", "--abbrev=0").ReadOutput())
	version := badversion.Parse(currentTagRev[1:])
	if currentTagRev != currentTag {
		if version.PreReleaseIdentifier == "" {
			version.Patch++
		}
	}
	return version, nil
}

func TestFlightVersion(version badversion.Version) string {
	return F.ToString(version.Major, ".", version.Minor, ".1000")
}
