import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '..', '..');

export interface CloudConfig {
  name: string;
  endpoint: string;
  apiKey: string;
}

export interface AppConfig {
  /** 监听的截图目录（设为系统截图目录即可零操作记账） */
  watchDir: string;
  /** 账本 JSON 文件路径 */
  dataFile: string;
  /** 本地服务端口 */
  port: number;
  /** OCR 模式：local=端侧Tesseract，cloud=云端 */
  ocrMode: 'local' | 'cloud';
  /** Tesseract 语言包 */
  tesseractLang: string;
  cloud: CloudConfig;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  return {
    watchDir: env.WATCH_DIR ?? resolve(projectRoot, 'inbox'),
    dataFile: env.DATA_FILE ?? resolve(projectRoot, 'data', 'ledger.json'),
    port: env.PORT ? Number(env.PORT) : 5173,
    ocrMode: env.OCR_MODE === 'cloud' ? 'cloud' : 'local',
    tesseractLang: env.TESSERACT_LANG ?? 'chi_sim+eng',
    cloud: {
      name: env.CLOUD_NAME ?? 'cloud',
      endpoint: env.CLOUD_ENDPOINT ?? '',
      apiKey: env.CLOUD_API_KEY ?? '',
    },
  };
}
