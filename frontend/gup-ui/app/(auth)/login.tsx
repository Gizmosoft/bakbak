import { zodResolver } from '@hookform/resolvers/zod';
import { Link, router, type Href } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { FormField } from '@/features/auth/components/FormField';
import { loginSchema, type LoginFormValues } from '@/features/auth/schemas';
import { formatAuthError } from '@/features/auth/utils';
import { VALIDATION } from '@/constants/validation';
import { useAuth } from '@/providers/AuthProvider';

/** Login route (/login). Validates form, calls AuthProvider.login, then navigates home. */
export default function LoginScreen() {
  const { login } = useAuth();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setApiError(null);
    try {
      await login({
        email: values.email.trim(),
        password: values.password,
      });
      router.replace('/');
    } catch (error) {
      setApiError(formatAuthError(error));
    }
  });

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.container}
          keyboardShouldPersistTaps="handled"
        >
          <Text style={styles.title}>Welcome back</Text>
          <Text style={styles.subtitle}>Log in to Gup</Text>

          <Controller
            control={control}
            name="email"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Email"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.email?.message}
                keyboardType="email-address"
                autoComplete="email"
                textContentType="emailAddress"
                maxLength={VALIDATION.email.max}
              />
            )}
          />

          <Controller
            control={control}
            name="password"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Password"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.password?.message}
                secureTextEntry
                autoComplete="password"
                textContentType="password"
                maxLength={VALIDATION.password.max}
              />
            )}
          />

          {apiError ? <Text style={styles.apiError}>{apiError}</Text> : null}

          <Pressable
            style={[styles.button, isSubmitting && styles.buttonDisabled]}
            onPress={onSubmit}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.buttonText}>Log in</Text>
            )}
          </Pressable>

          <Text style={styles.footer}>
            No account?{' '}
            <Link href={'/register' as Href} style={styles.link}>
              Sign up
            </Link>
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#fff',
  },
  flex: {
    flex: 1,
  },
  container: {
    flexGrow: 1,
    padding: 24,
    justifyContent: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1A1B3A',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 32,
  },
  apiError: {
    color: '#c62828',
    marginBottom: 16,
    fontSize: 14,
  },
  button: {
    backgroundColor: '#1A1B3A',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.7,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  footer: {
    marginTop: 24,
    textAlign: 'center',
    color: '#666',
    fontSize: 14,
  },
  link: {
    color: '#1A1B3A',
    fontWeight: '600',
  },
});
