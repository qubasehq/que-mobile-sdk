import * as React from 'react';

import { QueMobileSdkViewProps } from './QueMobileSdk.types';

export default function QueMobileSdkView(props: QueMobileSdkViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
