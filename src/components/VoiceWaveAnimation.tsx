import React, { useEffect, useRef } from 'react';
import { View, StyleSheet, Animated } from 'react-native';

interface VoiceWaveAnimationProps {
  isActive: boolean;
  amplitude?: number;
  style?: any;
}

export const VoiceWaveAnimation: React.FC<VoiceWaveAnimationProps> = ({
  isActive,
  amplitude = 0.5,
  style,
}) => {
  const waveCount = 7;
  const waveAnimations = useRef(
    Array.from({ length: waveCount }, () => new Animated.Value(0))
  ).current;
  const amplitudeAnim = useRef(new Animated.Value(0)).current;

  const waveColors = [
    '#8A2BE2', // BlueViolet
    '#4169E1', // RoyalBlue
    '#FF1493', // DeepPink
    '#9370DB', // MediumPurple
    '#00BFFF', // DeepSkyBlue
    '#FF69B4', // HotPink
    '#DA70D6', // Orchid
  ];

  useEffect(() => {
    // Animate amplitude changes
    Animated.timing(amplitudeAnim, {
      toValue: isActive ? amplitude : 0.15,
      duration: isActive ? 100 : 500,
      useNativeDriver: true,
    }).start();
  }, [isActive, amplitude]);

  useEffect(() => {
    // Create continuous wave animations
    const animations = waveAnimations.map((anim) => {
      const speed = 0.01 + Math.random() * 0.02;
      const duration = 5000 / (1 + speed * 100);

      return Animated.loop(
        Animated.timing(anim, {
          toValue: 1,
          duration: duration,
          useNativeDriver: true,
        })
      );
    });

    animations.forEach((anim) => anim.start());

    return () => {
      animations.forEach((anim) => anim.stop());
    };
  }, []);

  const renderWave = (index: number) => {
    const amplitudeMultiplier = 0.8 + Math.random() * 0.5;

    const translateY = waveAnimations[index].interpolate({
      inputRange: [0, 1],
      outputRange: [0, -10 * amplitudeMultiplier],
    });

    const scaleY = amplitudeAnim.interpolate({
      inputRange: [0, 1],
      outputRange: [0.15, 1],
    });

    return (
      <Animated.View
        key={index}
        style={[
          styles.wave,
          {
            backgroundColor: waveColors[index % waveColors.length],
            opacity: 0.5,
            transform: [
              { translateY },
              { scaleY: Animated.multiply(scaleY, amplitudeMultiplier) },
            ],
          },
        ]}
      />
    );
  };

  return (
    <View style={[styles.container, style]}>
      <View style={styles.wavesContainer}>
        {Array.from({ length: waveCount }, (_, i) => renderWave(i))}
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
    height: 150,
    overflow: 'hidden',
  },
  wavesContainer: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  wave: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 80,
    borderTopLeftRadius: 100,
    borderTopRightRadius: 100,
  },
});
