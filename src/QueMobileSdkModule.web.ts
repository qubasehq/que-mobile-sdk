import { registerWebModule, NativeModule } from 'expo';

import { QueMobileSdkModuleEvents } from './QueMobileSdk.types';

class QueMobileSdkModule extends NativeModule<QueMobileSdkModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(QueMobileSdkModule, 'QueMobileSdkModule');
