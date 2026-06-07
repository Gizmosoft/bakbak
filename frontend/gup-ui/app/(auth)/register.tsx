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
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { FormField } from '@/features/auth/components/FormField';
import { registerSchema, type RegisterFormValues } from '@/features/auth/schemas';
import { formatAuthError, toRegisterRequest } from '@/features/auth/utils';
import { VALIDATION } from '@/constants/validation';
import { useAuth } from '@/providers/AuthProvider';

/** Register route (/register). Validates form, calls AuthProvider.register, then navigates home. */
export default function RegisterScreen() {
  const { register } = useAuth();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      username: '',
      email: '',
      password: '',
      displayName: '',
      dateOfBirth: '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setApiError(null);
    try {
      await register(toRegisterRequest(values));
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
          <Text style={styles.title}>Create account</Text>
          <Text style={styles.subtitle}>Join Gup</Text>

          <Controller
            control={control}
            name="username"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Username"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.username?.message}
                autoComplete="username"
                textContentType="username"
                maxLength={VALIDATION.username.max}
              />
            )}
          />

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
                autoComplete="new-password"
                textContentType="newPassword"
                maxLength={VALIDATION.password.max}
              />
            )}
          />

          <Controller
            control={control}
            name="displayName"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Display name (optional)"
                value={value ?? ''}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.displayName?.message}
                autoComplete="name"
                textContentType="name"
                maxLength={VALIDATION.displayName.max}
              />
            )}
          />

          <Controller
            control={control}
            name="dateOfBirth"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Date of birth"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.dateOfBirth?.message}
                placeholder="YYYY-MM-DD"
                keyboardType="numbers-and-punctuation"
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
              <Text style={styles.buttonText}>Sign up</Text>
            )}
          </Pressable>

          <Text style={styles.footer}>
            Already have an account?{' '}
            <Link href={'/login' as Href} style={styles.link}>
              Log in
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
