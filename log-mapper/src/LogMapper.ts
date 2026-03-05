interface LogEntry {
  timestamp?: string;
  level: 'debug' | 'info' | 'warn' | 'error' | 'verbose';
  tag?: string;
  message: string;
  file?: string;
  line?: number;
  raw: string;
}

interface LogCategory {
  name: string;
  description: string;
  patterns: RegExp[];
  entries: LogEntry[];
}

interface LogReport {
  totalEntries: number;
  byLevel: Record<string, number>;
  categories: Map<string, LogCategory>;
  files: string[];
  generatedAt: Date;
}

const LOG_PATTERNS = {
  timber: /^\[LOG\] (?:Timber\.(?:d|e|w|i|v)\(|Log\.(?:d|e|w|i|v)\(([^,]+),\s*"([^"]+)"(?:,\s*([^)]+))?\)|"([^"]+)")/,
  kotlinTag: /^(.+\.kt):(\d+):\[LOG\]\s+Log\.(?:d|e|w|i|v)\("([^"]+)",\s*"([^"]+)"(?:,\s*([^)]+))?\)/,
  simple: /^\[LOG\]\s+"([^"]+)"/,
  timestamp: /(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+)/,
  monitor: /^(═══|╔═|╚═|║|▶|⚠|✓|✗|→)/
};

const CATEGORIES: Omit<LogCategory, 'entries'>[] = [
  {
    name: 'Graphics',
    description: 'OpenGL, GLRenderer, SurfaceBridge operations',
    patterns: [
      /GLRenderer/i,
      /SurfaceBridge/i,
      /OpenGL/i,
      /External texture/i,
      /EGL version/i,
      /mvpMatrix/i,
      /textureCoord/i
    ]
  },
  {
    name: 'UI Components',
    description: 'UI component lifecycle and events',
    patterns: [
      /BottomSheetGallery/i,
      /TimelineEditor/i,
      /VideoGallery/i,
      /PreloadViewModel/i,
      /ThumbnailViewModel/i
    ]
  },
  {
    name: 'Cache',
    description: 'Cache operations and management',
    patterns: [
      /cache/i,
      /Cache/i,
      /CACHE/i,
      /preload/i,
      /thumbnail.*load/i
    ]
  },
  {
    name: 'Media',
    description: 'Media playback and processing',
    patterns: [
      /ExoPlayer/i,
      /MediaExtractor/i,
      /MediaCodec/i,
      /video.*uri/i,
      /duration/i
    ]
  },
  {
    name: 'Timeline',
    description: 'Timeline operations and state',
    patterns: [
      /timeline/i,
      /Timeline/i,
      /TimelineEditor/i,
      /scroll.*velocity/i
    ]
  },
  {
    name: 'Error',
    description: 'Error and exception logs',
    patterns: [
      /Timber\.e/,
      /Log\.e/,
      /error/i,
      /Error/i,
      /failed/i,
      /Failed/i,
      /exception/i,
      /Exception/i
    ]
  }
];

export class LogMapper {
  private categories: Map<string, LogCategory> = new Map();
  private entries: LogEntry[] = [];
  private files: string[] = [];

  constructor() {
    CATEGORIES.forEach(cat => {
      this.categories.set(cat.name, { ...cat, entries: [] });
    });
  }

  async parseFile(filePath: string): Promise<void> {
    this.files.push(filePath);
    const content = await Bun.file(filePath).text();
    const lines = content.split('\n');

    for (const line of lines) {
      if (line.trim()) {
        const entry = this.parseLine(line);
        if (entry) {
          this.entries.push(entry);
          this.categorizeEntry(entry);
        }
      }
    }
  }

  private parseLine(line: string): LogEntry | null {
    if (LOG_PATTERNS.monitor.test(line)) {
      return null;
    }

    let level: LogEntry['level'] = 'info';
    let tag: string | undefined;
    let message = line;
    let file: string | undefined;
    let lineNum: number | undefined;

    const kotlinTagMatch = line.match(LOG_PATTERNS.kotlinTag);
    if (kotlinTagMatch) {
      file = kotlinTagMatch[1];
      lineNum = parseInt(kotlinTagMatch[2]);
      tag = kotlinTagMatch[3];
      message = kotlinTagMatch[4];
      level = this.extractLevelFromTag(tag);
    } else {
      const timberMatch = line.match(LOG_PATTERNS.timber);
      if (timberMatch) {
        tag = timberMatch[1];
        message = timberMatch[2] || timberMatch[4] || '';
        level = this.extractLevelFromTag(tag);
      }
    }

    const timestampMatch = line.match(LOG_PATTERNS.timestamp);
    const timestamp = timestampMatch ? timestampMatch[1] : undefined;

    return {
      timestamp,
      level,
      tag,
      message,
      file,
      line: lineNum,
      raw: line
    };
  }

  private extractLevelFromTag(tag: string | undefined): LogEntry['level'] {
    if (!tag) return 'info';
    
    const tagLower = tag.toLowerCase();
    if (tagLower.includes('error') || tagLower.includes('e')) return 'error';
    if (tagLower.includes('warn') || tagLower.includes('w')) return 'warn';
    if (tagLower.includes('debug') || tagLower.includes('d')) return 'debug';
    if (tagLower.includes('verbose') || tagLower.includes('v')) return 'verbose';
    return 'info';
  }

  private categorizeEntry(entry: LogEntry): void {
    for (const [name, category] of this.categories) {
      for (const pattern of category.patterns) {
        if (pattern.test(entry.raw)) {
          category.entries.push(entry);
          break;
        }
      }
    }
  }

  generateReport(): LogReport {
    const byLevel: Record<string, number> = {
      debug: 0,
      info: 0,
      warn: 0,
      error: 0,
      verbose: 0
    };

    for (const entry of this.entries) {
      byLevel[entry.level]++;
    }

    return {
      totalEntries: this.entries.length,
      byLevel,
      categories: this.categories,
      files: this.files,
      generatedAt: new Date()
    };
  }

  printReport(): void {
    const report = this.generateReport();
    
    console.log('═══════════════════════════════════════════════════════════');
    console.log('║                    LOG MAPPER REPORT                     ║');
    console.log('╚═══════════════════════════════════════════════════════════');
    console.log(`Generated at: ${report.generatedAt.toISOString()}`);
    console.log('');
    
    console.log('📊 SUMMARY');
    console.log(`Total entries: ${report.totalEntries}`);
    console.log(`Files analyzed: ${report.files.length}`);
    console.log('');

    console.log('📈 BY LOG LEVEL');
    for (const [level, count] of Object.entries(report.byLevel)) {
      const bar = '█'.repeat(Math.min(50, Math.ceil(count / 2)));
      console.log(`  ${level.padEnd(8)}: ${count.toString().padStart(5)} ${bar}`);
    }
    console.log('');

    console.log('📁 FILES ANALYZED');
    for (const file of report.files) {
      console.log(`  • ${file}`);
    }
    console.log('');

    console.log('📋 BY CATEGORY');
    for (const [name, category] of report.categories) {
      console.log(`\n  ${category.name}`);
      console.log(`  ${'─'.repeat(category.name.length)}`);
      console.log(`  ${category.description}`);
      console.log(`  Entries: ${category.entries.length}`);
      
      if (category.entries.length > 0) {
        const levels: Record<string, number> = { debug: 0, info: 0, warn: 0, error: 0, verbose: 0 };
        for (const entry of category.entries) {
          levels[entry.level]++;
        }
        console.log(`  Breakdown:`);
        for (const [level, count] of Object.entries(levels)) {
          if (count > 0) {
            console.log(`    • ${level}: ${count}`);
          }
        }
      }
    }
    console.log('');

    console.log('═══════════════════════════════════════════════════════════');
  }

  async saveReport(outputPath: string): Promise<void> {
    const report = this.generateReport();
    
    let output = `# Log Mapper Report\n\n`;
    output += `Generated at: ${report.generatedAt.toISOString()}\n\n`;
    output += `## Summary\n\n`;
    output += `- Total entries: ${report.totalEntries}\n`;
    output += `- Files analyzed: ${report.files.length}\n\n`;
    output += `## By Log Level\n\n`;
    
    for (const [level, count] of Object.entries(report.byLevel)) {
      output += `- ${level}: ${count}\n`;
    }
    
    output += `\n## Files Analyzed\n\n`;
    for (const file of report.files) {
      output += `- ${file}\n`;
    }
    
    output += `\n## By Category\n\n`;
    for (const [name, category] of report.categories) {
      output += `### ${category.name}\n\n`;
      output += `${category.description}\n\n`;
      output += `Entries: ${category.entries.length}\n\n`;
      
      if (category.entries.length > 0) {
        const levels: Record<string, number> = { debug: 0, info: 0, warn: 0, error: 0, verbose: 0 };
        for (const entry of category.entries) {
          levels[entry.level]++;
        }
        output += `Breakdown:\n`;
        for (const [level, count] of Object.entries(levels)) {
          if (count > 0) {
            output += `- ${level}: ${count}\n`;
          }
        }
        output += '\n';
      }
    }
    
    await Bun.write(outputPath, output);
    console.log(`Report saved to: ${outputPath}`);
  }
}
