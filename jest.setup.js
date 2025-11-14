// Jest setup file for testing

// Suppress console logs during tests
global.console = {
  ...console,
  log: jest.fn(),
  debug: jest.fn(),
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
};

// Mock TTS
jest.mock('react-native-tts', () => ({
  default: {
    speak: jest.fn(() => Promise.resolve()),
    stop: jest.fn(() => Promise.resolve()),
    addEventListener: jest.fn(),
    removeAllListeners: jest.fn(),
    setDefaultLanguage: jest.fn(() => Promise.resolve()),
    setDefaultRate: jest.fn(() => Promise.resolve()),
    setDefaultPitch: jest.fn(() => Promise.resolve()),
  },
  speak: jest.fn(() => Promise.resolve()),
  stop: jest.fn(() => Promise.resolve()),
  addEventListener: jest.fn(),
  removeAllListeners: jest.fn(),
  setDefaultLanguage: jest.fn(() => Promise.resolve()),
  setDefaultRate: jest.fn(() => Promise.resolve()),
  setDefaultPitch: jest.fn(() => Promise.resolve()),
}));

// Mock Voice
jest.mock('react-native-voice', () => ({
  default: {
    onSpeechStart: null,
    onSpeechEnd: null,
    onSpeechResults: null,
    onSpeechError: null,
    start: jest.fn(() => Promise.resolve()),
    stop: jest.fn(() => Promise.resolve()),
    cancel: jest.fn(() => Promise.resolve()),
    destroy: jest.fn(() => Promise.resolve()),
    removeAllListeners: jest.fn(),
    isAvailable: jest.fn(() => Promise.resolve(1)),
  },
}));

// Mock FS
jest.mock('react-native-fs', () => ({
  DocumentDirectoryPath: '/mock/documents',
  mkdir: jest.fn(() => Promise.resolve()),
  writeFile: jest.fn(() => Promise.resolve()),
  appendFile: jest.fn(() => Promise.resolve()),
  readFile: jest.fn(() => Promise.resolve('')),
  readDir: jest.fn(() => Promise.resolve([])),
  exists: jest.fn(() => Promise.resolve(true)),
  moveFile: jest.fn(() => Promise.resolve()),
}));

// Mock Accessibility Module
jest.mock('./src/native/AccessibilityModule', () => ({
  dumpHierarchy: jest.fn(() => Promise.resolve('<hierarchy></hierarchy>')),
  clickOnPoint: jest.fn(() => Promise.resolve(true)),
  longPressOnPoint: jest.fn(() => Promise.resolve(true)),
  typeText: jest.fn(() => Promise.resolve(true)),
  scroll: jest.fn(() => Promise.resolve(true)),
  performBack: jest.fn(() => Promise.resolve(true)),
  performHome: jest.fn(() => Promise.resolve(true)),
  performRecents: jest.fn(() => Promise.resolve(true)),
  pressEnter: jest.fn(() => Promise.resolve(true)),
  isKeyboardOpen: jest.fn(() => Promise.resolve(false)),
  getCurrentActivity: jest.fn(() => Promise.resolve('com.example.MainActivity')),
  openApp: jest.fn(() => Promise.resolve(true)),
  findPackageByAppName: jest.fn(() => Promise.resolve('com.example.app')),
  getScreenDimensions: jest.fn(() => Promise.resolve({ width: 1080, height: 1920 })),
  getScrollInfo: jest.fn(() => Promise.resolve({ pixelsAbove: 0, pixelsBelow: 0 })),
}));
