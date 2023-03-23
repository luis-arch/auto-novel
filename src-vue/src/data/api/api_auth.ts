import api from './api';
import { Ok, Err, Result } from './result';

export interface SignInDto {
  email: string;
  username: string;
  token: string;
  expiresAt: number;
}

async function signIn(
  emailOrUsername: string,
  password: string
): Promise<Result<SignInDto>> {
  return api
    .post(`auth/sign-in`, { json: { emailOrUsername, password } })
    .json<SignInDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function signUp(
  email: string,
  emailCode: string,
  username: string,
  password: string
): Promise<Result<SignInDto>> {
  return api
    .post('auth/sign-up', {
      json: {
        email,
        emailCode,
        username,
        password,
      },
    })
    .json<SignInDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function verifyEmail(email: string): Promise<Result<string>> {
  return api
    .post('auth/verify-email', {
      searchParams: { email },
    })
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  signIn,
  signUp,
  verifyEmail,
};