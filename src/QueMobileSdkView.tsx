import { requireNativeView } from 'expo';
import * as React from 'react';

import { QueMobileSdkViewProps } from './QueMobileSdk.types';

const NativeView: React.ComponentType<QueMobileSdkViewProps> =
  requireNativeView('QueMobileSdk');

export default function QueMobileSdkView(props: QueMobileSdkViewProps) {
  return <NativeView {...props} />;
}
