import { Pressable, StyleSheet, Text, View } from 'react-native';

type ErrorStateProps = {
  message: string;
  onRetry?: () => void;
  retryLabel?: string;
};

export function ErrorState({
  message,
  onRetry,
  retryLabel = 'Retry',
}: ErrorStateProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Something went wrong</Text>
      <Text style={styles.message}>{message}</Text>
      {onRetry ? (
        <Pressable style={styles.button} onPress={onRetry}>
          <Text style={styles.buttonText}>{retryLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1A1B3A',
    marginBottom: 8,
    textAlign: 'center',
  },
  message: {
    fontSize: 14,
    color: '#c62828',
    textAlign: 'center',
    marginBottom: 16,
  },
  button: {
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  buttonText: {
    color: '#1A1B3A',
    fontWeight: '600',
    fontSize: 16,
  },
});
