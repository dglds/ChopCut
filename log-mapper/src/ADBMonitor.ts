import { spawn, ChildProcess } from 'child_process';

/**
 * Níveis de filtro de log do Android
 */
export type AndroidLogLevel = 'V' | 'D' | 'I' | 'W' | 'E';

/**
 * Opções de configuração do monitor ADB
 */
export interface ADBMonitorOptions {
  /**
   * Tag para filtrar logs (ex: 'ChopCut', 'GLRenderer')
   */
  tag?: string;

  /**
   * Nível mínimo de log (V, D, I, W, E)
   * @default 'V'
   */
  level?: AndroidLogLevel;

  /**
   * Filtra por processo específico
   */
  pid?: string;

  /**
   * Filtra por pacote específico
   */
  package?: string;

  /**
   * Buffer de saída do logcat em KB
   * @default 100
   */
  buffer?: number;

  /**
   * Limpa o buffer antes de começar
   * @default true
   */
  clearBuffer?: boolean;

  /**
   * Filtra mensagens por expressão regular
   */
  regex?: string;

  /**
   * Formato de saída do logcat
   * @default 'time'
   */
  format?: 'brief' | 'process' | 'tag' | 'thread' | 'raw' | 'time' | 'threadtime' | 'long';

  /**
   * Callback chamado para cada linha de log
   */
  onLogLine?: (line: string) => void;

  /**
   * Callback chamado quando ocorre erro
   */
  onError?: (error: Error) => void;

  /**
   * Callback chamado quando o monitor termina
   */
  onClose?: (code: number | null) => void;
}

/**
 * Resultado do comando ADB
 */
export interface ADBCommandResult {
  /**
   * Saída padrão (stdout)
   */
  stdout: string;

  /**
   * Saída de erro (stderr)
   */
  stderr: string;

  /**
   * Código de saída do processo
   */
  exitCode: number | null;

  /**
   * Indica se o comando foi bem-sucedido
   */
  success: boolean;
}

/**
 * Informações sobre dispositivos conectados
 */
export interface ADBDevice {
  /**
   * ID do dispositivo
   */
  id: string;

  /**
   * Estado do dispositivo
   */
  status: 'device' | 'offline' | 'unauthorized';

  /**
   * Produto
   */
  product?: string;

  /**
   * Modelo
   */
  model?: string;

  /**
   * Dispositivo
   */
  device?: string;

  /**
   * Nível de API do Android
   */
  apiLevel?: number;
}

/**
 * Estatísticas do monitor
 */
export interface MonitorStats {
  /**
   * Número de linhas processadas
   */
  linesProcessed: number;

  /**
   * Número de erros capturados
   */
  errorsCaptured: number;

  /**
   * Tempo de execução em segundos
   */
  uptime: number;

  /**
   * Linhas por segundo
   */
  linesPerSecond: number;
}

/**
 * Classe para executar comandos ADB
 */
export class ADBCommand {
  /**
   * Executa um comando ADB síncrono
   * @param args - Argumentos do comando ADB
   * @returns Promise com o resultado
   */
  static async execute(...args: string[]): Promise<ADBCommandResult> {
    return new Promise((resolve) => {
      const process = spawn('adb', args);
      let stdout = '';
      let stderr = '';

      process.stdout?.on('data', (data) => {
        stdout += data.toString();
      });

      process.stderr?.on('data', (data) => {
        stderr += data.toString();
      });

      process.on('close', (code) => {
        resolve({
          stdout,
          stderr,
          exitCode: code,
          success: code === 0
        });
      });
    });
  }

  /**
   * Executa um comando ADB assíncrono com callback
   * @param args - Argumentos do comando ADB
   * @param onLine - Callback para cada linha de saída
   * @returns ChildProcess para controle
   */
  static executeAsync(
    args: string[],
    onLine: (line: string) => void
  ): ChildProcess {
    const process = spawn('adb', args);

    process.stdout?.on('data', (data) => {
      const lines = data.toString().split('\n');
      for (const line of lines) {
        if (line.trim()) {
          onLine(line);
        }
      }
    });

    process.stderr?.on('data', (data) => {
      const lines = data.toString().split('\n');
      for (const line of lines) {
        if (line.trim()) {
          onLine(line);
        }
      }
    });

    return process;
  }

  /**
   * Lista dispositivos conectados
   * @returns Promise com lista de dispositivos
   */
  static async getDevices(): Promise<ADBDevice[]> {
    const result = await this.execute('devices', '-l');
    if (!result.success) {
      throw new Error(`Failed to get devices: ${result.stderr}`);
    }

    const devices: ADBDevice[] = [];
    const lines = result.stdout.split('\n').slice(1);

    for (const line of lines) {
      const match = line.match(/([^\s]+)\s+(device|offline|unauthorized)(?:\s+(.+))?/);
      if (match) {
        const device: ADBDevice = {
          id: match[1],
          status: match[2] as any
        };

        if (match[3]) {
          const info = match[3];
          const productMatch = info.match(/product:(\S+)/);
          const modelMatch = info.match(/model:(\S+)/);
          const deviceMatch = info.match(/device:(\S+)/);

          if (productMatch) device.product = productMatch[1];
          if (modelMatch) device.model = modelMatch[1];
          if (deviceMatch) device.device = deviceMatch[1];
        }

        devices.push(device);
      }
    }

    return devices;
  }

  /**
   * Obtém o PID de um pacote
   * @param packageName - Nome do pacote
   * @returns Promise com o PID ou null
   */
  static async getPackagePid(packageName: string): Promise<number | null> {
    const result = await this.execute('shell', 'pidof', packageName);
    if (result.success && result.stdout.trim()) {
      return parseInt(result.stdout.trim());
    }
    return null;
  }

  /**
   * Limpa o buffer do logcat
   */
  static async clearLogcat(): Promise<void> {
    await this.execute('logcat', '-c');
  }

  /**
   * Obtém propriedades do dispositivo
   * @param property - Propriedade (ex: 'ro.build.version.sdk')
   * @returns Promise com valor da propriedade
   */
  static async getProperty(property: string): Promise<string> {
    const result = await this.execute('shell', 'getprop', property);
    return result.stdout.trim();
  }

  /**
   * Obtém o nível de API do Android
   * @returns Promise com nível da API
   */
  static async getAPILevel(): Promise<number> {
    const value = await this.getProperty('ro.build.version.sdk');
    return parseInt(value);
  }

  /**
   * Verifica se o ADB está disponível
   * @returns Promise indicando disponibilidade
   */
  static async isAvailable(): Promise<boolean> {
    try {
      const result = await this.execute('version');
      return result.success;
    } catch {
      return false;
    }
  }
}

/**
 * Monitor de logs do Android via ADB
 */
export class ADBMonitor {
  private process: ChildProcess | null = null;
  private startTime: number = 0;
  private linesProcessed: number = 0;
  private errorsCaptured: number = 0;
  private isRunning: boolean = false;

  constructor(private options: ADBMonitorOptions = {}) {}

  /**
   * Inicia o monitor de logs
   * @returns Promise que resolve quando o monitor inicia
   */
  async start(): Promise<void> {
    if (this.isRunning) {
      throw new Error('Monitor is already running');
    }

    const available = await ADBCommand.isAvailable();
    if (!available) {
      throw new Error('ADB is not available. Make sure ADB is installed and in PATH.');
    }

    const devices = await ADBCommand.getDevices();
    if (devices.length === 0) {
      throw new Error('No devices connected. Connect a device and enable USB debugging.');
    }

    const device = devices[0];
    if (device.status !== 'device') {
      throw new Error(`Device ${device.id} is ${device.status}. Authorize the device on the phone.`);
    }

    if (this.options.clearBuffer !== false) {
      await ADBCommand.clearLogcat();
      console.log('✓ Logcat buffer cleared');
    }

    const args = await this.buildLogcatArgs();
    console.log(`📱 Starting monitor on device: ${device.id}`);
    console.log(`🔧 Command: adb ${args.join(' ')}`);

    this.startTime = Date.now();
    this.linesProcessed = 0;
    this.errorsCaptured = 0;
    this.isRunning = true;

    this.process = ADBCommand.executeAsync(args, (line) => {
      this.linesProcessed++;

      if (this.options.onLogLine) {
        this.options.onLogLine(line);
      }

      if (line.includes('Error') || line.includes('error') || line.includes('E/')) {
        this.errorsCaptured++;
      }
    });

    this.process.on('error', (error) => {
      this.isRunning = false;
      if (this.options.onError) {
        this.options.onError(error);
      }
    });

    this.process.on('close', (code) => {
      this.isRunning = false;
      console.log(`\n✗ Monitor stopped with code: ${code}`);
      if (this.options.onClose) {
        this.options.onClose(code);
      }
    });
  }

  /**
   * Para o monitor de logs
   */
  stop(): void {
    if (this.process) {
      this.process.kill();
      this.process = null;
      this.isRunning = false;
      console.log('\n✓ Monitor stopped');
    }
  }

  /**
   * Verifica se o monitor está rodando
   */
  isRunningMonitor(): boolean {
    return this.isRunning;
  }

  /**
   * Obtém estatísticas do monitor
   */
  getStats(): MonitorStats {
    const uptime = (Date.now() - this.startTime) / 1000;
    const linesPerSecond = uptime > 0 ? this.linesProcessed / uptime : 0;

    return {
      linesProcessed: this.linesProcessed,
      errorsCaptured: this.errorsCaptured,
      uptime,
      linesPerSecond
    };
  }

  /**
   * Constrói os argumentos do comando logcat
   * @private
   */
  private async buildLogcatArgs(): Promise<string[]> {
    const args: string[] = ['logcat'];

    if (this.options.format) {
      args.push(`-v${this.options.format}`);
    }

    if (this.options.buffer) {
      args.push(`-G${this.options.buffer}K`);
    }

    let filters: string[] = [];

    if (this.options.tag) {
      if (this.options.level) {
        filters.push(`${this.options.tag}:${this.options.level}`);
      } else {
        filters.push(`${this.options.tag}:*`);
      }
    }

    if (this.options.package) {
      try {
        const apiLevel = await ADBCommand.getAPILevel();
        // --package is supported in API 24+ (Android 7.0)
        if (apiLevel >= 24) {
          args.push(`--package=${this.options.package}`);
        } else {
          const pid = await this.getPidForPackage(this.options.package);
          if (pid) {
            args.push(`--pid=${pid}`);
          }
        }
      } catch (error) {
        console.warn('⚠️  Could not determine API level, falling back to PID lookup.');
        const pid = await this.getPidForPackage(this.options.package);
        if (pid) {
          args.push(`--pid=${pid}`);
        }
      }
    } else if (this.options.pid) {
      args.push(`--pid=${this.options.pid}`);
    }

    if (filters.length > 0) {
      args.push(...filters);
    }

    if (this.options.regex) {
      args.push(this.options.regex);
    }

    return args;
  }

  /**
   * Obtém PID para um pacote
   * @private
   */
  private async getPidForPackage(packageName: string): Promise<number | null> {
    try {
      return await ADBCommand.getPackagePid(packageName);
    } catch {
      return null;
    }
  }

  /**
   * Imprime estatísticas em tempo real
   */
  printStats(): void {
    const stats = this.getStats();
    console.log(`\n📊 Stats:`);
    console.log(`  Lines: ${stats.linesProcessed}`);
    console.log(`  Errors: ${stats.errorsCaptured}`);
    console.log(`  Uptime: ${stats.uptime.toFixed(1)}s`);
    console.log(`  Rate: ${stats.linesPerSecond.toFixed(1)} lines/s`);
  }
}

export default ADBMonitor;
