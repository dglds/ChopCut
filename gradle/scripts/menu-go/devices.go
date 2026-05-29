package main

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//                 GERENCIAMENTO DE CONEXÃO / DISPOSITIVOS (ADB + mDNS)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Tudo relacionado a descoberta, listagem e pareamento de dispositivos Android vive
// aqui, isolado da orquestração de tarefas Gradle e da renderização do painel.

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type deviceTickMsg time.Time
type refreshDevicesMsg struct {
	devices []string
}

func getAdbPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Android/Sdk/platform-tools/adb")
}

func getDevices() ([]string, error) {
	adbPath := getAdbPath()
	cmd := exec.Command(adbPath, "devices")
	out, err := cmd.Output()
	if err != nil {
		return nil, err
	}
	var devices []string
	lines := strings.Split(string(out), "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "List of devices") {
			continue
		}
		parts := strings.Fields(line)
		if len(parts) >= 2 && parts[1] == "device" {
			devices = append(devices, parts[0])
		}
	}
	return devices, nil
}

func tryMdnsConnect() {
	adbPath := getAdbPath()
	cmd := exec.Command("avahi-browse", "-trp", "_adb._tcp")
	out, err := cmd.Output()
	if err != nil {
		return
	}
	lines := strings.Split(string(out), "\n")
	for _, line := range lines {
		if !strings.HasPrefix(line, "=") {
			continue
		}
		parts := strings.Split(line, ";")
		if len(parts) >= 9 {
			addr := parts[7]
			port := parts[8]
			if addr != "" && port != "" {
				checkCmd := exec.Command(adbPath, "devices")
				devsOut, _ := checkCmd.Output()
				if !strings.Contains(string(devsOut), addr+":"+port) {
					program.Send(lineMsg(fmt.Sprintf("📡 Conectando a %s:%s via mDNS...", addr, port)))
					exec.Command(adbPath, "connect", addr+":"+port).Run()
				}
			}
		}
	}
}

func deviceTick() tea.Cmd {
	return tea.Tick(3*time.Second, func(t time.Time) tea.Msg {
		return deviceTickMsg(t)
	})
}

func (m model) refreshDevicesCmd() tea.Cmd {
	return func() tea.Msg {
		var list []string
		adbPath := getAdbPath()
		cmd := exec.Command(adbPath, "devices")
		out, err := cmd.Output()
		if err == nil {
			lines := strings.Split(string(out), "\n")
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if line == "" || strings.HasPrefix(line, "List of devices") {
					continue
				}
				parts := strings.Fields(line)
				if len(parts) >= 2 {
					status := parts[1]
					devName := parts[0]

					styledStatus := status
					if status == "device" {
						styledStatus = lipgloss.NewStyle().Foreground(lipgloss.Color("46")).Render("ativo")
					} else if status == "unauthorized" {
						styledStatus = lipgloss.NewStyle().Foreground(lipgloss.Color("196")).Render("não autorizado")
					} else {
						styledStatus = lipgloss.NewStyle().Foreground(lipgloss.Color("208")).Render(status)
					}

					list = append(list, fmt.Sprintf("  • %s (%s)", devName, styledStatus))
				}
			}
		}
		if len(list) == 0 {
			list = []string{"  Nenhum dispositivo encontrado."}
		}
		return refreshDevicesMsg{devices: list}
	}
}
