import React, { useEffect, useRef } from 'react';
import { View, StyleSheet, Animated, Dimensions, Easing } from 'react-native';

interface VoiceWaveAnimationProps {
  isActive: boolean;
  amplitude?: number;
  style?: any;
  height?: number;
}

const { width: SCREEN_WIDTH } = Dimensions.get('window');

export const VoiceWaveAnimationAdvanced: React.FC<VoiceWaveAnimationProps> = ({
  isActive,
  amplitude = 0.5,
  style,
  height = 150,
}) => {
  const waveCount = 7;
  const minIdleAmplitude = 0.15;
  const maxWaveHeightScale = 0.25;

  // Animation values for each wave
  const wavePhases = useRef(
    Array.from({ length: waveCount }, () => new Animated.Value(0))
  ).current;

  const amplitudeAnim = useRef(new Animated.Value(minIdleAmplitude)).current;

  const waveColors = [
    'rgba(138, 43, 226, 0.5)',  // BlueViolet
    'rgba(65, 105, 225, 0.5)',  // RoyalBlue
    'rgba(255, 20, 147, 0.5)',  // DeepPink
    'rgba(147, 112, 219, 0.5)', // MediumPurple
    'rgba(0, 191, 255, 0.5)',   // DeepSkyBlue
    'rgba(255, 105, 180, 0.5)', // HotPink
    'rgba(218, 112, 214, 0.5)', // Orchid
  ];

  // Wave properties
  const waveProperties = useRef(
    Array.from({ length: waveCount }, () => ({
      frequency: 0.8 + Math.random() * 0.6,
      speed: 0.01 + Math.random() * 0.02,
      amplitudeMultiplier: 0.8 + Math.random() * 0.5,
    }))
  ).current;

  useEffect(() => {
    // Animate amplitude based on active state
    const targetAmplitude = isActive
      ? minIdleAmplitude + amplitude * maxWaveHeightScale
      : minIdleAmplitude;

    Animated.timing(amplitudeAnim, {
      toValue: targetAmplitude,
      duration: isActive ? 100 : 500,
      easing: Easing.inOut(Easing.ease),
      useNativeDriver: false,
    }).start();
  }, [isActive, amplitude]);

  useEffect(() => {
    // Create continuous phase animations for each wave
    const animations = wavePhases.map((phase) => {
      const duration = 5000;

      return Animated.loop(
        Animated.timing(phase, {
          toValue: 1,
          duration: duration,
          easing: Easing.linear,
          useNativeDriver: false,
        })
      );
    });

    animations.forEach((anim) => anim.start());

    return () => {
      animations.forEach((anim) => anim.stop());
    };
  }, []);

  const renderWave = (index: number) => {
    const { frequency, amplitudeMultiplier } = waveProperties[index];
    const color = waveColors[index % waveColors.length];

    // Create wave shape using multiple animated views
    const waveElements = [];
    const segments = 20;

    for (let i = 0; i <= segments; i++) {
      const x = (i / segments) * SCREEN_WIDTH;
      const phase = wavePhases[index];

      const translateY = Animated.multiply(
        Animated.add(
          phase,
          Animated.multiply(amplitudeAnim, amplitudeMultiplier)
        ),
        height * 0.3
      );

      const sineOffset = Math.sin((i / segments) * Math.PI * 2 * frequency) * 20;

      waveElements.push(
        <Animated.View
          key={`${index}-${i}`}
          style={[
            styles.waveSegment,
            {
              left: x,
              backgroundColor: color,
              transform: [
                {
                  translateY: Animated.add(
                    translateY,
                    new Animated.Value(sineOffset)
                  ),
                },
              ],
            },
          ]}
        />
      );
    }

    return waveElements;
  };

  return (
    <View style={[styles.container, style, { height }]} pointerEvents="none">
      <View style={styles.wavesContainer}>
        {Array.from({ length: waveCount }, (_, i) => (
          <View key={i} style={styles.waveLayer}>
            {renderWave(i)}
          </View>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    overflow: 'hidden',
    backgroundColor: 'transparent',
  },
  wavesContainer: {
    flex: 1,
    position: 'relative',
  },
  waveLayer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: '100%',
  },
  waveSegment: {
    position: 'absolute',
    bottom: 0,
    width: SCREEN_WIDTH / 20,
    height: 100,
    borderTopLeftRadius: 50,
    borderTopRightRadius: 50,
  },
});
