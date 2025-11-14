import { NativeModule, requireNativeModule } from 'expo';

import { QueMobileSdkModuleEvents } from './QueMobileSdk.types';

declare class QueMobileSdkModule extends NativeModule<QueMobileSdkModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<QueMobileSdkModule>('QueMobileSdk');
