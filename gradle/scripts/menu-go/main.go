package main

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

var options = []string{
	"build",
	"build and install",
	"clean build and install",
	"clean build and cache",
	"connect device",
	"check sintax (lintDebug)",
	"Sair do Painel",
}

type lineMsg string
type taskFinishedMsg struct {
	err error
}
type tickMsg time.Time
type deviceTickMsg time.Time
type refreshDevicesMsg struct {
	devices []string
}

type model struct {
	width            int
	height           int
	selected         int
	logLines         []string
	logViewport      viewport.Model
	runningTask      bool
	taskTitle        string
	taskStartTime    time.Time
	lastApkStatus    string
	activeCmd        *exec.Cmd
	mutex            sync.Mutex
	gradleParams     map[string]bool
	quitting         bool
	connectedDevices []string
}

var program *tea.Program

func main() {
	vp := viewport.New(0, 0)
	vp.SetContent("Aguardando início...")

	m := model{
		logViewport:      vp,
		lastApkStatus:    getApkStatus(),
		gradleParams:     parseParams("gradle/scripts/gradle-params.sh"),
		connectedDevices: []string{"  Buscando dispositivos..."},
	}

	// Remove variáveis conflitantes do ambiente que poderiam travar o TUI
	os.Unsetenv("BOLD")
	os.Unsetenv("UNDERLINE")

	p := tea.NewProgram(m, tea.WithAltScreen())
	program = p

	if _, err := p.Run(); err != nil {
		fmt.Printf("Erro ao iniciar o painel: %v\n", err)
		os.Exit(1)
	}
}

func parseParams(path string) map[string]bool {
	params := make(map[string]bool)
	file, err := os.Open(path)
	if err != nil {
		return params
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if strings.HasPrefix(line, "#") || !strings.Contains(line, "=") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			val := strings.ToLower(strings.TrimSpace(parts[1]))
			params[key] = (val == "true")
		}
	}
	return params
}

func getGradleArgs(params map[string]bool) []string {
	var args []string

	// Verbosidade
	if params["GRADLE_QUIET"] {
		args = append(args, "-q")
	}
	if params["GRADLE_WARN"] {
		args = append(args, "-w")
	}
	if params["GRADLE_INFO"] {
		args = append(args, "-i")
	}
	if params["GRADLE_DEBUG"] {
		args = append(args, "-d")
	}

	// Stacktrace
	if params["GRADLE_STACKTRACE"] {
		args = append(args, "-s")
	}
	if params["GRADLE_FULL_STACKTRACE"] {
		args = append(args, "-S")
	}

	// Performance
	if params["GRADLE_PARALLEL"] {
		args = append(args, "--parallel")
	}
	if params["GRADLE_BUILD_CACHE"] {
		args = append(args, "--build-cache")
	}
	if params["GRADLE_CONFIGURE_ON_DEMAND"] {
		args = append(args, "--configure-on-demand")
	}
	if params["GRADLE_LIMIT_WORKERS"] {
		args = append(args, "--max-workers=4")
	}

	// Modos especiais
	if params["GRADLE_DAEMON"] {
		args = append(args, "--daemon")
	} else if v, ok := params["GRADLE_DAEMON"]; ok && !v {
		args = append(args, "--no-daemon")
	}
	if params["GRADLE_OFFLINE"] {
		args = append(args, "--offline")
	}
	if params["GRADLE_RERUN_TASKS"] {
		args = append(args, "--rerun-tasks")
	}
	if params["GRADLE_CONTINUE"] {
		args = append(args, "--continue")
	}
	if params["GRADLE_DRY_RUN"] {
		args = append(args, "-m")
	}

	return args
}

func getAdbPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Android/Sdk/platform-tools/adb")
}

func getApkStatus() string {
	apkPath := "app/build/outputs/apk/debug/app-debug.apk"
	statusPath := ".last_build_status"

	info, err := os.Stat(apkPath)
	if err != nil {
		return "   APK: Nenhum (Sem build)"
	}

	apkTime := info.ModTime().Format("02/01 15:04")
	status := "Sucesso"
	if data, err := os.ReadFile(statusPath); err == nil {
		status = strings.TrimSpace(string(data))
	}

	statusStyle := lipgloss.NewStyle().Bold(true)
	if status == "Sucesso" {
		statusStyle = statusStyle.Foreground(lipgloss.Color("46"))
	} else {
		statusStyle = statusStyle.Foreground(lipgloss.Color("196"))
	}

	return fmt.Sprintf("   APK: %s (%s)", apkTime, statusStyle.Render(status))
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

func tick() tea.Cmd {
	return tea.Tick(time.Second, func(t time.Time) tea.Msg {
		return tickMsg(t)
	})
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

func (m *model) startTask(taskOpt int) tea.Cmd {
	m.runningTask = true
	m.taskStartTime = time.Now()
	m.logLines = []string{}
	m.logViewport.SetContent("")

	title := options[taskOpt]
	m.taskTitle = title

	m.gradleParams = parseParams("gradle/scripts/gradle-params.sh")
	gradleArgs := getGradleArgs(m.gradleParams)

	return func() tea.Msg {
		var err error

		logLine := func(line string) {
			program.Send(lineMsg(line))
		}

		runCmd := func(name string, args []string) error {
			m.mutex.Lock()
			m.activeCmd = exec.Command(name, args...)
			m.activeCmd.Dir = "."
			m.activeCmd.Env = append(os.Environ(), "JAVA_HOME="+filepath.Join(".", "jdk17"))
			cmdCopy := m.activeCmd
			m.mutex.Unlock()

			stdout, err := cmdCopy.StdoutPipe()
			if err != nil {
				return err
			}
			stderr, err := cmdCopy.StderrPipe()
			if err != nil {
				return err
			}

			if err := cmdCopy.Start(); err != nil {
				return err
			}

			var wg sync.WaitGroup
			wg.Add(2)

			reader := func(r io.Reader) {
				defer wg.Done()
				scanner := bufio.NewScanner(r)
				for scanner.Scan() {
					program.Send(lineMsg(scanner.Text()))
				}
			}

			go reader(stdout)
			go reader(stderr)

			wg.Wait()
			return cmdCopy.Wait()
		}

		checkDevice := func() ([]string, error) {
			logLine("🔌 Aguardando dispositivo Android...")
			tryMdnsConnect()
			
			// wait-for-device desbloqueia quando há atividade USB/mDNS
			waitErr := runCmd(getAdbPath(), []string{"wait-for-device"})
			if waitErr != nil {
				return nil, waitErr
			}
			
			devices, devErr := getDevices()
			if devErr != nil || len(devices) == 0 {
				logLine("⚠️  Nenhum dispositivo ativo ou autorizado foi encontrado.")
				logLine("   Por favor, verifique se:")
				logLine("   1. O aparelho está conectado fisicamente via USB ou pareado via rede.")
				logLine("   2. A opção 'Depuração USB' está ativa em 'Opções do desenvolvedor'.")
				logLine("   3. Você autorizou o acesso para este computador na janela que aparece na tela do aparelho.")
				return nil, fmt.Errorf("nenhum dispositivo ativo ou autorizado")
			}
			
			logLine("✅ Dispositivo conectado com sucesso!")
			for _, dev := range devices {
				logLine("📱 Ativo: " + dev)
			}
			return devices, nil
		}

		switch taskOpt {
		case 0: // build
			logLine("Iniciando build: build (assembleDebug)...")
			logLine(fmt.Sprintf("Parâmetros ativos: %s", strings.Join(gradleArgs, " ")))
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			err = runCmd("./gradlew", append([]string{"assembleDebug"}, gradleArgs...))

		case 1: // build and install
			logLine("Iniciando tarefa: build and install (installDebug)...")
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			var devices []string
			devices, err = checkDevice()
			if err == nil {
				err = runCmd("./gradlew", append([]string{"installDebug"}, gradleArgs...))
				if err == nil {
					logLine("📲 Abrindo aplicativo nos dispositivos conectados...")
					for _, dev := range devices {
						runCmd(getAdbPath(), []string{"-s", dev, "shell", "am", "start", "-n", "com.chopcut/com.chopcut.MainActivity"})
					}
				}
			}

		case 2: // clean build and install
			logLine("Iniciando tarefa: clean build and install...")
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			var devices []string
			devices, err = checkDevice()
			if err == nil {
				logLine("🧹 Limpando cache do Gradle (.gradle)...")
				os.RemoveAll(".gradle")
				logLine("🗑️ Limpando compilações antigas (clean)...")
				err = runCmd("./gradlew", append([]string{"clean"}, gradleArgs...))
				if err == nil {
					err = runCmd("./gradlew", append([]string{"installDebug"}, gradleArgs...))
					if err == nil {
						logLine("📲 Abrindo aplicativo nos dispositivos conectados...")
						for _, dev := range devices {
							runCmd(getAdbPath(), []string{"-s", dev, "shell", "am", "start", "-n", "com.chopcut/com.chopcut.MainActivity"})
						}
					}
				}
			}

		case 3: // clean build and cache
			logLine("Iniciando tarefa: clean build and cache...")
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			logLine("🧹 Limpando cache do Gradle (.gradle)...")
			os.RemoveAll(".gradle")
			logLine("🗑️ Limpando compilações antigas (clean)...")
			err = runCmd("./gradlew", append([]string{"clean"}, gradleArgs...))
			if err == nil {
				err = runCmd("./gradlew", append([]string{"assembleDebug"}, gradleArgs...))
			}

		case 4: // connect device
			logLine("Iniciando tarefa: connect device...")
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			_, err = checkDevice()

		case 5: // check syntax (lintDebug)
			logLine("Iniciando tarefa: check syntax (lintDebug)...")
			logLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
			err = runCmd("./gradlew", append([]string{"lintDebug"}, gradleArgs...))
		}

		return taskFinishedMsg{err: err}
	}
}

func (m model) Init() tea.Cmd {
	return tea.Batch(m.refreshDevicesCmd(), deviceTick())
}

func (m model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		if m.runningTask {
			// Se estiver executando tarefa, esc ou ctrl+c aborta
			if msg.Type == tea.KeyEsc || (msg.Type == tea.KeyCtrlC && msg.String() == "ctrl+c") {
				m.mutex.Lock()
				if m.activeCmd != nil && m.activeCmd.Process != nil {
					m.activeCmd.Process.Kill()
					m.logLines = append(m.logLines, "\n❌ TAREFA CANCELADA PELO USUÁRIO.")
					m.logViewport.SetContent(strings.Join(m.logLines, "\n"))
					m.logViewport.GotoBottom()
				}
				m.mutex.Unlock()
				return m, nil
			}
			return m, nil
		}

		switch msg.String() {
		case "up", "k":
			if m.selected > 0 {
				m.selected--
			}
		case "down", "j":
			if m.selected < len(options)-1 {
				m.selected++
			}
		case "enter":
			if m.selected == len(options)-1 {
				m.quitting = true
				return m, tea.Quit
			}
			return m, tea.Batch(m.startTask(m.selected), tick())
		case "1", "2", "3", "4", "5", "6":
			idx := int(msg.Runes[0] - '1')
			m.selected = idx
			return m, tea.Batch(m.startTask(idx), tick())
		case "0":
			m.selected = len(options) - 1
			m.quitting = true
			return m, tea.Quit
		case "q", "ctrl+c":
			m.quitting = true
			return m, tea.Quit
		}

	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		
		leftWidth := 42
		rightWidth := (msg.Width - 4) - leftWidth
		if rightWidth < 40 {
			rightWidth = 40
		}
		
		boxHeight := msg.Height - 4
		if boxHeight < 10 {
			boxHeight = 10
		}
		viewportHeight := boxHeight - 4
		if viewportHeight < 5 {
			viewportHeight = 5
		}
		
		m.logViewport.Width = rightWidth - 2
		m.logViewport.Height = viewportHeight

	case lineMsg:
		m.logLines = append(m.logLines, string(msg))
		if len(m.logLines) > 1000 {
			m.logLines = m.logLines[len(m.logLines)-1000:]
		}
		m.logViewport.SetContent(strings.Join(m.logLines, "\n"))
		m.logViewport.GotoBottom()

	case tickMsg:
		if m.runningTask {
			return m, tick()
		}

	case deviceTickMsg:
		return m, tea.Batch(m.refreshDevicesCmd(), deviceTick())

	case refreshDevicesMsg:
		m.connectedDevices = msg.devices

	case taskFinishedMsg:
		m.runningTask = false
		m.activeCmd = nil

		statusFile := ".last_build_status"
		if msg.err == nil {
			os.WriteFile(statusFile, []byte("Sucesso"), 0644)
			m.logLines = append(m.logLines, "\n✨ TAREFA CONCLUÍDA COM SUCESSO!")
		} else {
			if strings.Contains(msg.err.Error(), "killed") || strings.Contains(msg.err.Error(), "interrupt") {
				// Já foi logado pelo cancelamento
			} else {
				os.WriteFile(statusFile, []byte("Falhou"), 0644)
				m.logLines = append(m.logLines, fmt.Sprintf("\n💥 OCORREU UM ERRO: %v", msg.err))
			}
		}
		m.logViewport.SetContent(strings.Join(m.logLines, "\n"))
		m.logViewport.GotoBottom()
		m.lastApkStatus = getApkStatus()
		return m, m.refreshDevicesCmd()
	}

	return m, nil
}

func (m model) View() string {
	if m.quitting {
		return ""
	}

	// 1. Barra de Título Superior
	titleStyle := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("212")).
		Border(lipgloss.DoubleBorder()).
		BorderForeground(lipgloss.Color("57")).
		Padding(0, 1).
		MarginBottom(1).
		Align(lipgloss.Center).
		Width(m.width - 4)

	title := titleStyle.Render("🚀 CHOPCUT GRADLE PANEL")

	// Determinação precisa e enxuta da coluna esquerda para economizar espaço
	leftWidth := 42
	rightWidth := (m.width - 4) - leftWidth
	if rightWidth < 40 {
		rightWidth = 40
	}

	// Estica os painéis para ocupar a altura inteira do terminal (100% de aproveitamento útil)
	boxHeight := m.height - 4
	if boxHeight < 10 {
		boxHeight = 10
	}

	// 2. Coluna Esquerda: Menu de Opções
	var leftContent strings.Builder

	leftContent.WriteString(lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("212")).Render("📂 TAREFAS GRADLE\n"))
	leftContent.WriteString(strings.Repeat("─", leftWidth-2) + "\n\n")

	for i, opt := range options {
		var line string
		hotkey := i + 1
		if i == 6 {
			hotkey = 0
		}
		if i == m.selected {
			lineStyle := lipgloss.NewStyle().
				Foreground(lipgloss.Color("255")).
				Background(lipgloss.Color("57")).
				Bold(true)
			line = lineStyle.Render(fmt.Sprintf(" > [%d] %s ", hotkey, opt))
		} else {
			line = fmt.Sprintf("   [%d] %s", hotkey, opt)
		}
		leftContent.WriteString(line + "\n")
	}

	leftContent.WriteString("\n" + strings.Repeat("─", leftWidth-2) + "\n\n")
	leftContent.WriteString(lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("220")).Render("📱 DISPOSITIVOS CONECTADOS\n"))
	for _, dev := range m.connectedDevices {
		leftContent.WriteString(dev + "\n")
	}

	leftStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("212")).
		Width(leftWidth).
		Height(boxHeight).
		Padding(0, 1)

	leftCol := leftStyle.Render(leftContent.String())

	// 3. Coluna Direita: Row 1 Header (inline) + Row 2 Viewport (Output)
	var rightContent strings.Builder

	// Row 1: Inline Header com informações do build ou tarefa em andamento
	var inlineHeader string
	if m.runningTask {
		taskName := lipgloss.NewStyle().Foreground(lipgloss.Color("208")).Bold(true).Render(m.taskTitle)
		duration := lipgloss.NewStyle().Foreground(lipgloss.Color("226")).Render(fmt.Sprintf("%s", time.Since(m.taskStartTime).Round(time.Second)))
		cancelPrompt := lipgloss.NewStyle().Foreground(lipgloss.Color("196")).Bold(true).Render("[Esc] Cancelar")
		inlineHeader = fmt.Sprintf("⏳ RODANDO: %s (%s) | %s", taskName, duration, cancelPrompt)
	} else {
		inlineHeader = fmt.Sprintf("📊 ÚLTIMA EXECUÇÃO: %s", strings.TrimSpace(m.lastApkStatus))
	}
	
	rightContent.WriteString(" " + inlineHeader + "\n")
	
	// Linha divisória horizontal estilizada separando o header do log
	rightContent.WriteString(lipgloss.NewStyle().Foreground(lipgloss.Color("57")).Render(strings.Repeat("─", rightWidth-4)) + "\n")

	// Configuração do Viewport de Logs (Row 2)
	viewportHeight := boxHeight - 4
	if viewportHeight < 5 {
		viewportHeight = 5
	}
	m.logViewport.Width = rightWidth - 2
	m.logViewport.Height = viewportHeight
	
	// Adiciona a view do viewport de logs com estilo de branco suave (gentil para leitura)
	logStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("253"))
	rightContent.WriteString(logStyle.Render(m.logViewport.View()))

	rightStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("57")).
		Width(rightWidth).
		Height(boxHeight).
		Padding(0, 1)

	rightCol := rightStyle.Render(rightContent.String())

	// 4. Junção das Colunas e Renderização
	mainLayout := lipgloss.JoinHorizontal(lipgloss.Top, leftCol, rightCol)

	return lipgloss.JoinVertical(lipgloss.Left, title, mainLayout)
}
