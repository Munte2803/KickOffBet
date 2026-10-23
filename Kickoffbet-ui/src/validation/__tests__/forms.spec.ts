import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ZodTypeAny } from 'zod'
import {
  adminCreateSchema,
  createMatchSchema,
  depositSchema,
  emailSchema,
  leagueSchema,
  loginSchema,
  registerSchema,
  resetPasswordSchema,
  stakeSchema,
  teamSchema,
  withdrawSchema,
} from '@/validation/forms'

/** Returns a map of field path -> first error message, or null when the value is valid. */
function errorsOf(schema: ZodTypeAny, value: unknown): Record<string, string> | null {
  const result = schema.safeParse(value)
  if (result.success) return null
  const errors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const key = issue.path.join('.')
    errors[key] ??= issue.message
  }
  return errors
}

const TODAY = new Date(2026, 5, 15, 12, 0, 0) // 15 June 2026, local time

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(TODAY)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('loginSchema', () => {
  it('accepts a valid email and any non-empty password', () => {
    expect(errorsOf(loginSchema, { email: 'user@example.com', password: 'x' })).toBeNull()
  })

  it('trims the email', () => {
    expect(loginSchema.parse({ email: '  user@example.com ', password: 'x' }).email).toBe('user@example.com')
  })

  it('requires email and password', () => {
    expect(errorsOf(loginSchema, { email: '', password: '' })).toEqual({
      email: 'Emailul este obligatoriu',
      password: 'Parola este obligatorie',
    })
  })

  it('treats a whitespace-only email as missing', () => {
    expect(errorsOf(loginSchema, { email: '   ', password: 'x' })?.email).toBe('Emailul este obligatoriu')
  })

  it('rejects malformed emails', () => {
    expect(errorsOf(loginSchema, { email: 'not-an-email', password: 'x' })?.email).toBe('Format de email invalid')
  })

  it('does not apply password complexity rules on login', () => {
    expect(errorsOf(loginSchema, { email: 'user@example.com', password: 'weak' })).toBeNull()
  })
})

const validFile = () => new File(['id'], 'buletin.png', { type: 'image/png' })

const validRegistration = () => ({
  firstName: 'Ana-Maria',
  lastName: "O'Neil Ștefănescu",
  email: 'ana@example.com',
  password: 'Parola123',
  confirmPassword: 'Parola123',
  birthDate: '2000-01-01',
  idCard: validFile(),
})

describe('registerSchema', () => {
  it('accepts a valid registration', () => {
    expect(errorsOf(registerSchema, validRegistration())).toBeNull()
  })

  describe('names', () => {
    it.each([
      ['A', 'Numele trebuie sa aiba intre 2 si 50 de caractere'],
      ['A'.repeat(51), 'Numele trebuie sa aiba intre 2 si 50 de caractere'],
      ['Ana2', 'Numele contine caractere invalide'],
      ['Ana@', 'Numele contine caractere invalide'],
    ])('rejects first name %j', (firstName, message) => {
      expect(errorsOf(registerSchema, { ...validRegistration(), firstName })?.firstName).toBe(message)
    })

    it('accepts 2 and 50 character names and unicode letters, apostrophes, hyphens and spaces', () => {
      for (const name of ['Al', 'A'.repeat(50), 'Ștefan', 'Jean-Luc', "D'Arc", 'Maria Ioana']) {
        expect(errorsOf(registerSchema, { ...validRegistration(), firstName: name, lastName: name }), name).toBeNull()
      }
    })

    it('trims names before checking their length', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), lastName: '  A  ' })?.lastName).toBe(
        'Numele trebuie sa aiba intre 2 si 50 de caractere',
      )
      expect(registerSchema.parse({ ...validRegistration(), lastName: '  Pop ' }).lastName).toBe('Pop')
    })
  })

  describe('password rules (match backend: upper, lower, digit, min 8)', () => {
    it.each([
      ['parola123', 'missing uppercase'],
      ['PAROLA123', 'missing lowercase'],
      ['ParolaMea', 'missing digit'],
      ['Pa1aaaa', 'only 7 characters'],
    ])('rejects %j (%s)', (password) => {
      const errors = errorsOf(registerSchema, { ...validRegistration(), password, confirmPassword: password })
      expect(errors?.password).toBe('Parola trebuie sa contina litera mare, litera mica si o cifra')
    })

    it('accepts exactly 8 characters with all classes, including symbols', () => {
      for (const password of ['Pa1aaaaa', 'P@r0la!x']) {
        expect(errorsOf(registerSchema, { ...validRegistration(), password, confirmPassword: password })).toBeNull()
      }
    })

    it('requires a password', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), password: '' })?.password).toBe('Parola este obligatorie')
    })

    it('requires a matching confirmation, reported on confirmPassword', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), confirmPassword: 'Parola124' })).toEqual({
        confirmPassword: 'Parolele nu coincid',
      })
    })

    it('requires the confirmation', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), confirmPassword: '' })?.confirmPassword).toBe(
        'Confirmarea parolei este obligatorie',
      )
    })
  })

  describe('birth date', () => {
    it('requires a value', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), birthDate: '' })?.birthDate).toBe(
        'Data nasterii este obligatorie',
      )
    })

    it('rejects an unparsable date', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), birthDate: 'abc' })?.birthDate).toBe(
        'Data nasterii este obligatorie',
      )
    })

    it('rejects a future date', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), birthDate: '2026-06-16' })?.birthDate).toBe(
        'Data nasterii trebuie sa fie in trecut',
      )
    })

    it('accepts someone who turns 18 today', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), birthDate: '2008-06-15' })).toBeNull()
    })

    it('rejects someone who turns 18 tomorrow or later this year', () => {
      for (const birthDate of ['2008-06-16', '2008-12-31', '2010-01-01']) {
        expect(errorsOf(registerSchema, { ...validRegistration(), birthDate })?.birthDate, birthDate).toBe(
          'Trebuie sa ai cel putin 18 ani pentru a te inregistra',
        )
      }
    })

    it('accepts someone who turned 18 earlier this year', () => {
      expect(errorsOf(registerSchema, { ...validRegistration(), birthDate: '2008-01-01' })).toBeNull()
    })
  })

  it('requires an ID card file', () => {
    expect(errorsOf(registerSchema, { ...validRegistration(), idCard: undefined })?.idCard).toBe(
      'Buletinul este obligatoriu',
    )
    expect(errorsOf(registerSchema, { ...validRegistration(), idCard: 'buletin.png' })?.idCard).toBe(
      'Buletinul este obligatoriu',
    )
  })

  it('validates the email', () => {
    expect(errorsOf(registerSchema, { ...validRegistration(), email: 'nope' })?.email).toBe('Format de email invalid')
  })
})

describe('adminCreateSchema', () => {
  const { idCard: _idCard, ...validAdmin } = validRegistration()

  it('accepts a valid admin without an ID card', () => {
    expect(errorsOf(adminCreateSchema, validAdmin)).toBeNull()
  })

  it('applies the same password, confirmation, name and age rules', () => {
    expect(errorsOf(adminCreateSchema, { ...validAdmin, password: 'weakpass', confirmPassword: 'weakpass' })?.password).toBe(
      'Parola trebuie sa contina litera mare, litera mica si o cifra',
    )
    expect(errorsOf(adminCreateSchema, { ...validAdmin, confirmPassword: 'Other123' })?.confirmPassword).toBe(
      'Parolele nu coincid',
    )
    expect(errorsOf(adminCreateSchema, { ...validAdmin, firstName: 'X' })?.firstName).toBeTruthy()
    expect(errorsOf(adminCreateSchema, { ...validAdmin, birthDate: '2010-01-01' })?.birthDate).toBe(
      'Trebuie sa ai cel putin 18 ani pentru a te inregistra',
    )
  })
})

describe('emailSchema', () => {
  it('validates presence and format', () => {
    expect(errorsOf(emailSchema, { email: 'a@b.ro' })).toBeNull()
    expect(errorsOf(emailSchema, { email: '' })?.email).toBe('Emailul este obligatoriu')
    expect(errorsOf(emailSchema, { email: 'a@' })?.email).toBe('Format de email invalid')
  })
})

describe('resetPasswordSchema', () => {
  const valid = { token: 'tok', newPassword: 'Parola123', confirmPassword: 'Parola123' }

  it('accepts a valid reset', () => {
    expect(errorsOf(resetPasswordSchema, valid)).toBeNull()
  })

  it('requires the token', () => {
    expect(errorsOf(resetPasswordSchema, { ...valid, token: '' })?.token).toBe('Tokenul de resetare este obligatoriu')
  })

  it('enforces password complexity and confirmation', () => {
    expect(errorsOf(resetPasswordSchema, { ...valid, newPassword: 'parola123', confirmPassword: 'parola123' })?.newPassword).toBe(
      'Parola trebuie sa contina litera mare, litera mica si o cifra',
    )
    expect(errorsOf(resetPasswordSchema, { ...valid, newPassword: '' })?.newPassword).toBe('Parola este obligatorie')
    expect(errorsOf(resetPasswordSchema, { ...valid, confirmPassword: 'Parola124' })).toEqual({
      confirmPassword: 'Parolele nu coincid',
    })
  })
})

describe('depositSchema / withdrawSchema', () => {
  it('accepts the inclusive bounds and coerces numeric strings', () => {
    expect(depositSchema.parse({ amount: '20' }).amount).toBe(20)
    expect(errorsOf(depositSchema, { amount: 200000 })).toBeNull()
    expect(withdrawSchema.parse({ amount: '150.75' }).amount).toBe(150.75)
    expect(errorsOf(withdrawSchema, { amount: 200000 })).toBeNull()
  })

  it('rejects amounts outside the limits', () => {
    expect(errorsOf(depositSchema, { amount: 19.99 })?.amount).toBe('Depunerea minima este 20 RON')
    expect(errorsOf(depositSchema, { amount: 200000.01 })?.amount).toBe('Depunerea maxima este 200000 RON')
    expect(errorsOf(withdrawSchema, { amount: 5 })?.amount).toBe('Retragerea minima este 20 RON')
    expect(errorsOf(withdrawSchema, { amount: 300000 })?.amount).toBe('Retragerea maxima este 200000 RON')
  })

  it('accepts up to 2 decimals and rejects more, like the backend (@Digits fraction = 2)', () => {
    for (const amount of [20.1, 20.07, 150.75, 199999.99, '33.33']) {
      expect(errorsOf(depositSchema, { amount }), String(amount)).toBeNull()
      expect(errorsOf(withdrawSchema, { amount }), String(amount)).toBeNull()
    }
    expect(errorsOf(depositSchema, { amount: 20.123 })?.amount).toBe('Suma poate avea maxim 2 zecimale')
    expect(errorsOf(withdrawSchema, { amount: '150.755' })?.amount).toBe('Suma poate avea maxim 2 zecimale')
  })

  it('rejects non-numeric input', () => {
    expect(errorsOf(depositSchema, { amount: 'abc' })).not.toBeNull()
    expect(errorsOf(withdrawSchema, { amount: 'abc' })).not.toBeNull()
  })
})

describe('leagueSchema', () => {
  it('accepts a valid league with or without teams', () => {
    expect(errorsOf(leagueSchema, { name: 'Liga 1', code: 'RO1' })).toBeNull()
    expect(errorsOf(leagueSchema, { name: 'Liga 1', code: 'RO1', teamIds: ['a', 'b'] })).toBeNull()
  })

  it('requires a name and a 2-5 character code', () => {
    expect(errorsOf(leagueSchema, { name: '  ', code: 'RO1' })?.name).toBe('Numele este obligatoriu')
    expect(errorsOf(leagueSchema, { name: 'L', code: 'R' })?.code).toBe('Codul trebuie sa aiba intre 2 si 5 caractere')
    expect(errorsOf(leagueSchema, { name: 'L', code: 'ROMAN' })).toBeNull()
    expect(errorsOf(leagueSchema, { name: 'L', code: 'ROMANI' })?.code).toBe('Codul trebuie sa aiba intre 2 si 5 caractere')
  })
})

describe('teamSchema', () => {
  const valid = { name: 'FC Steaua', shortName: 'Steaua', tla: 'FCS' }

  it('accepts a valid team', () => {
    expect(errorsOf(teamSchema, valid)).toBeNull()
    expect(errorsOf(teamSchema, { ...valid, leagueIds: ['l1'] })).toBeNull()
  })

  it('requires name, short name and a 1-5 character TLA', () => {
    expect(errorsOf(teamSchema, { ...valid, name: '' })?.name).toBe('Numele este obligatoriu')
    expect(errorsOf(teamSchema, { ...valid, shortName: ' ' })?.shortName).toBe('Numele scurt este obligatoriu')
    expect(errorsOf(teamSchema, { ...valid, tla: '' })?.tla).toBe('Prescurtarea este obligatorie')
    expect(errorsOf(teamSchema, { ...valid, tla: 'ABCDEF' })?.tla).toBe('Prescurtarea trebuie sa aiba intre 1 si 5 caractere')
    expect(errorsOf(teamSchema, { ...valid, tla: 'A' })).toBeNull()
  })
})

describe('createMatchSchema', () => {
  const valid = { startTime: '2026-06-20T18:00', leagueId: 'l1', homeTeamId: 'h', awayTeamId: 'a' }

  it('accepts a valid future match', () => {
    expect(errorsOf(createMatchSchema, valid)).toBeNull()
  })

  it('requires a valid start time in the future', () => {
    expect(errorsOf(createMatchSchema, { ...valid, startTime: '' })?.startTime).toBe('Data de start este obligatorie')
    expect(errorsOf(createMatchSchema, { ...valid, startTime: 'bad' })?.startTime).toBe('Data de start este obligatorie')
    expect(errorsOf(createMatchSchema, { ...valid, startTime: '2026-06-15T11:59' })?.startTime).toBe(
      'Data de start trebuie sa fie in viitor',
    )
  })

  it('requires league and both teams', () => {
    expect(errorsOf(createMatchSchema, { ...valid, leagueId: '' })?.leagueId).toBe('Liga este obligatorie')
    expect(errorsOf(createMatchSchema, { ...valid, homeTeamId: '' })?.homeTeamId).toBe('Echipa gazda este obligatorie')
    expect(errorsOf(createMatchSchema, { ...valid, awayTeamId: '' })?.awayTeamId).toBe('Echipa oaspete este obligatorie')
  })

  it('rejects the same team on both sides, reported on awayTeamId', () => {
    expect(errorsOf(createMatchSchema, { ...valid, awayTeamId: 'h' })).toEqual({
      awayTeamId: 'Echipa gazda si echipa oaspete nu pot fi aceleasi',
    })
  })
})

describe('stakeSchema', () => {
  it('accepts stakes between 2 and 50000 with at most two decimals', () => {
    for (const stake of [2, 50000, 10.5, 10.25, '7.99']) {
      expect(errorsOf(stakeSchema, { stake }), String(stake)).toBeNull()
    }
  })

  it('rejects stakes outside the limits', () => {
    expect(errorsOf(stakeSchema, { stake: 1.99 })?.stake).toBe('Miza minima este 2 RON')
    expect(errorsOf(stakeSchema, { stake: 50000.01 })?.stake).toBe('Miza maxima este 50000 RON')
  })

  it('rejects more than two decimals', () => {
    expect(errorsOf(stakeSchema, { stake: 10.123 })?.stake).toBe('Miza poate avea maxim 2 zecimale')
  })
})
