import { describe, expect, it } from 'vitest'
import {
  canActivateUser,
  canApproveIdCard,
  canRejectIdCard,
  canSuspendUser,
  canUploadIdCard,
  canViewUserIdCard,
  type UserAccountState,
} from '@/utils/user-rules'
import type { UserStatus } from '@/types/enums'

const user = (overrides: Partial<UserAccountState> = {}): UserAccountState => ({
  role: 'USER',
  status: 'PENDING',
  idCardVerified: false,
  idCardUrl: 'id-cards/a.png',
  ...overrides,
})

describe('ID card approval / rejection', () => {
  it('offers both for a pending user with an uploaded, unverified card', () => {
    expect(canApproveIdCard(user())).toBe(true)
    expect(canRejectIdCard(user())).toBe(true)
  })

  it('needs an uploaded card (the backend refuses approving without one)', () => {
    expect(canApproveIdCard(user({ idCardUrl: null }))).toBe(false)
    expect(canRejectIdCard(user({ idCardUrl: null }))).toBe(false)
  })

  it('offers neither once the card is verified', () => {
    expect(canApproveIdCard(user({ idCardVerified: true }))).toBe(false)
    expect(canRejectIdCard(user({ idCardVerified: true }))).toBe(false)
  })

  it('rejects only PENDING users, like the backend', () => {
    expect(canRejectIdCard(user({ status: 'DECLINED' }))).toBe(false)
    expect(canRejectIdCard(user({ status: 'DEACTIVATED' }))).toBe(false)
    // a re-upload after a decline puts the user back to PENDING server side; approving still works
    expect(canApproveIdCard(user({ status: 'DECLINED' }))).toBe(true)
  })

  it('does not approve active, suspended or admin accounts', () => {
    expect(canApproveIdCard(user({ status: 'ACTIVE' }))).toBe(false)
    expect(canApproveIdCard(user({ status: 'SUSPENDED' }))).toBe(false)
    expect(canApproveIdCard(user({ role: 'ADMIN' }))).toBe(false)
    expect(canRejectIdCard(user({ role: 'ADMIN' }))).toBe(false)
  })
})

describe('suspend / activate', () => {
  it.each<UserStatus>(['PENDING', 'ACTIVE', 'DECLINED', 'DEACTIVATED'])('suspends a %s user', (status) => {
    expect(canSuspendUser(user({ status }))).toBe(true)
  })

  it('never suspends an admin or an already suspended user', () => {
    expect(canSuspendUser(user({ role: 'ADMIN', status: 'ACTIVE' }))).toBe(false)
    expect(canSuspendUser(user({ status: 'SUSPENDED' }))).toBe(false)
  })

  it.each<[UserStatus, boolean]>([
    ['SUSPENDED', true],
    ['DEACTIVATED', true],
    ['DECLINED', true],
    ['ACTIVE', false],
    ['PENDING', false],
  ])('activation of a %s user: %s', (status, expected) => {
    expect(canActivateUser(user({ status }))).toBe(expected)
  })
})

describe('viewing and uploading the ID card', () => {
  it('lets the admin view an uploaded card unless the account is suspended', () => {
    expect(canViewUserIdCard(user())).toBe(true)
    expect(canViewUserIdCard(user({ idCardUrl: null }))).toBe(false)
    expect(canViewUserIdCard(user({ status: 'SUSPENDED' }))).toBe(false)
  })

  it('lets a pending or declined user without a card upload one', () => {
    expect(canUploadIdCard(user({ idCardUrl: null }))).toBe(true)
    expect(canUploadIdCard(user({ status: 'DECLINED', idCardUrl: null }))).toBe(true)
  })

  it('does not allow an upload while a card is under review, verified, or for other statuses', () => {
    expect(canUploadIdCard(user())).toBe(false)
    expect(canUploadIdCard(user({ idCardUrl: null, idCardVerified: true }))).toBe(false)
    expect(canUploadIdCard(user({ idCardUrl: null, status: 'ACTIVE' }))).toBe(false)
    expect(canUploadIdCard(user({ idCardUrl: null, status: 'SUSPENDED' }))).toBe(false)
  })
})
