import type { UserProfile } from '@/types/profile.types'

/**
 * Which account actions the UI offers, kept in line with the backend rules
 * (services/users/impl/UserServiceImpl.java). The UI may be stricter than the backend (it hides
 * actions that would be surprising, e.g. activating an unverified account), never looser.
 */
export type UserAccountState = Pick<UserProfile, 'role' | 'status' | 'idCardVerified' | 'idCardUrl'>

const isAdmin = (user: UserAccountState) => user.role === 'ADMIN'

/**
 * Approving validates the uploaded ID card. Backend: refused for ACTIVE users and without an uploaded
 * card. Not offered for admins or suspended users (approval could reactivate a suspended account).
 */
export function canApproveIdCard(user: UserAccountState): boolean {
  if (isAdmin(user) || user.status === 'ACTIVE' || user.status === 'SUSPENDED') return false
  return Boolean(user.idCardUrl) && !user.idCardVerified
}

/** Backend: only a PENDING user whose ID card is not verified yet; it deletes the uploaded card. */
export function canRejectIdCard(user: UserAccountState): boolean {
  if (isAdmin(user)) return false
  return user.status === 'PENDING' && !user.idCardVerified && Boolean(user.idCardUrl)
}

/** Backend: never an admin, and not a user who is already suspended. */
export function canSuspendUser(user: UserAccountState): boolean {
  return !isAdmin(user) && user.status !== 'SUSPENDED'
}

/**
 * Backend: any user who is not ACTIVE. The UI only offers it to reverse a suspension, a
 * deactivation or a decline; PENDING accounts become active through the ID card approval.
 */
export function canActivateUser(user: UserAccountState): boolean {
  return user.status === 'SUSPENDED' || user.status === 'DEACTIVATED' || user.status === 'DECLINED'
}

/** The admin may open the uploaded card, except for a suspended account. */
export function canViewUserIdCard(user: UserAccountState): boolean {
  return user.status !== 'SUSPENDED' && Boolean(user.idCardUrl)
}

/**
 * Backend: PENDING or DECLINED, with no verified card. The UI also waits while an uploaded card
 * is under review (no replacing it in the meantime).
 */
export function canUploadIdCard(user: UserAccountState): boolean {
  return (
    !user.idCardVerified && !user.idCardUrl && (user.status === 'PENDING' || user.status === 'DECLINED')
  )
}
