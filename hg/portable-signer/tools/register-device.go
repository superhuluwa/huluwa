//go:build ignore

// Build this file from the checked-out upstream module so the public SDK is used
// directly, without copying device registration or introducing Android tooling.
package main

import (
    "context"
    "encoding/json"
    "flag"
    "fmt"
    "os"
    "path/filepath"
    "time"

    "github.com/beilunyang/openhongguo/adapters"
    "github.com/beilunyang/openhongguo/adapters/filestore"
    "github.com/beilunyang/openhongguo/sdk"
)

func main() {
    target := flag.String("profile","/config/anonymous.json","device profile path")
    replace := flag.Bool("replace",false,"explicitly replace an existing profile, retaining a backup")
    flag.Parse()
    if _,err:=os.Stat(*target);err==nil && !*replace { fmt.Println("existing device profile retained");return }
    if err:=os.MkdirAll(filepath.Dir(*target),0700);err!=nil { fail() }
    file,err:=os.CreateTemp(filepath.Dir(*target),".registration-*.json")
    if err!=nil { fail() }
    temporary:=file.Name();file.Close();os.Remove(temporary);defer os.Remove(temporary)
    zero:=0
    client,err:=adapters.NewClient(temporary,sdk.Options{RetryAttempts:1,DeviceResets:&zero})
    if err!=nil { fail() }
    ctx,cancel:=context.WithTimeout(context.Background(),45*time.Second)
    defer cancel()
    if err:=client.ResetDevice(ctx);err!=nil { fail() }
    p,err:=filestore.New(temporary).Load(ctx)
    if err!=nil || p.DeviceID=="" || p.InstallID=="" || p.CDID=="" { fail() }
    if previous,err:=os.ReadFile(*target);err==nil {
        if !*replace { fmt.Fprintln(os.Stderr,"profile appeared during registration; retained it");return }
        backup:=*target+".before-registration-"+time.Now().UTC().Format("20060102T150405Z")
        if err:=os.WriteFile(backup,previous,0600);err!=nil { fail() }
    }
    if err:=os.Rename(temporary,*target);err!=nil { fail() }
    if err:=os.Chmod(*target,0600);err!=nil { fail() }
    json.NewEncoder(os.Stdout).Encode(map[string]any{"ok":true,"engine":"openhongguo-sdk","androidRequired":false})
}

func fail() { fmt.Fprintln(os.Stderr,"anonymous device registration failed; existing profile retained");os.Exit(1) }
