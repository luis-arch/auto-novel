import { UserRole } from '@/model/User';

export interface AuthData {
  profile?: {
    token: string;
    username: string;
    role: UserRole;
    createdAt: number;
    expiredAt: number;
    issuedAt: number;
  };
  adminMode: boolean;
}

export const migrate = (data: AuthData) => {
  if (data.profile?.issuedAt === undefined) {
    data.profile = undefined;
  }
};
