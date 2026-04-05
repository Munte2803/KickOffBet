import { z } from 'zod'
import {
  MAX_DEPOSIT,
  MAX_STAKE,
  MAX_WITHDRAWAL,
  MIN_DEPOSIT,
  MIN_STAKE,
  MIN_WITHDRAWAL,
} from '@/constants/betting.constants'

const nameRegex = /^[\p{L}\s'-]+$/u
const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/
const firstNameField = z
  .string()
  .trim()
  .min(2, 'Numele trebuie sa aiba intre 2 si 50 de caractere')
  .max(50, 'Numele trebuie sa aiba intre 2 si 50 de caractere')
  .regex(nameRegex, 'Numele contine caractere invalide')
const lastNameField = z
  .string()
  .trim()
  .min(2, 'Numele trebuie sa aiba intre 2 si 50 de caractere')
  .max(50, 'Numele trebuie sa aiba intre 2 si 50 de caractere')
  .regex(nameRegex, 'Numele contine caractere invalide')
const emailField = z.string().trim().min(1, 'Emailul este obligatoriu').email('Format de email invalid')
const passwordField = z
  .string()
  .min(1, 'Parola este obligatorie')
  .regex(passwordRegex, 'Parola trebuie sa contina litera mare, litera mica si o cifra')
const birthDateField = z
  .string()
  .min(1, 'Data nasterii este obligatorie')
  .refine((value) => !Number.isNaN(new Date(`${value}T00:00:00`).getTime()), 'Data nasterii este obligatorie')
  .refine((value) => new Date(`${value}T00:00:00`) < new Date(), 'Data nasterii trebuie sa fie in trecut')
  .refine(isAdult, 'Trebuie sa ai cel putin 18 ani pentru a te inregistra')

function isAdult(dateString: string) {
  const birthDate = new Date(`${dateString}T00:00:00`)
  if (Number.isNaN(birthDate.getTime())) return false

  const today = new Date()
  let age = today.getFullYear() - birthDate.getFullYear()
  const monthDiff = today.getMonth() - birthDate.getMonth()

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
    age -= 1
  }

  return age >= 18
}

export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'Parola este obligatorie'),
})

export const registerSchema = z.object({
  firstName: firstNameField,
  lastName: lastNameField,
  email: emailField,
  password: passwordField,
  confirmPassword: z.string().min(1, 'Confirmarea parolei este obligatorie'),
  birthDate: birthDateField,
  idCard: z.custom<File>((value) => value instanceof File, 'Buletinul este obligatoriu'),
}).refine((value) => value.password === value.confirmPassword, {
  message: 'Parolele nu coincid',
  path: ['confirmPassword'],
})

export const adminCreateSchema = z.object({
  firstName: firstNameField,
  lastName: lastNameField,
  email: emailField,
  password: passwordField,
  confirmPassword: z.string().min(1, 'Confirmarea parolei este obligatorie'),
  birthDate: birthDateField,
}).refine((value) => value.password === value.confirmPassword, {
  message: 'Parolele nu coincid',
  path: ['confirmPassword'],
})

export const emailSchema = z.object({
  email: z.string().trim().min(1, 'Emailul este obligatoriu').email('Format de email invalid'),
})

export const resetPasswordSchema = z.object({
  token: z.string().min(1, 'Tokenul de resetare este obligatoriu'),
  newPassword: z
    .string()
    .min(1, 'Parola este obligatorie')
    .regex(passwordRegex, 'Parola trebuie sa contina litera mare, litera mica si o cifra'),
  confirmPassword: z.string().min(1, 'Confirmarea parolei este obligatorie'),
}).refine((value) => value.newPassword === value.confirmPassword, {
  message: 'Parolele nu coincid',
  path: ['confirmPassword'],
})

export const depositSchema = z.object({
  amount: z.coerce
    .number()
    .min(MIN_DEPOSIT, `Depunerea minima este ${MIN_DEPOSIT} RON`)
    .max(MAX_DEPOSIT, `Depunerea maxima este ${MAX_DEPOSIT} RON`),
})

export const withdrawSchema = z.object({
  amount: z.coerce
    .number()
    .min(MIN_WITHDRAWAL, `Retragerea minima este ${MIN_WITHDRAWAL} RON`)
    .max(MAX_WITHDRAWAL, `Retragerea maxima este ${MAX_WITHDRAWAL} RON`),
})

export const leagueSchema = z.object({
  name: z.string().trim().min(1, 'Numele este obligatoriu'),
  code: z.string().trim().min(2, 'Codul trebuie sa aiba intre 2 si 5 caractere').max(5, 'Codul trebuie sa aiba intre 2 si 5 caractere'),
  teamIds: z.array(z.string()).optional(),
})

export const teamSchema = z.object({
  name: z.string().trim().min(1, 'Numele este obligatoriu'),
  shortName: z.string().trim().min(1, 'Numele scurt este obligatoriu'),
  tla: z.string().trim().min(1, 'Prescurtarea este obligatorie').max(5, 'Prescurtarea trebuie sa aiba intre 1 si 5 caractere'),
  leagueIds: z.array(z.string()).optional(),
})

export const createMatchSchema = z.object({
  startTime: z
    .string()
    .min(1, 'Data de start este obligatorie')
    .refine((value) => !Number.isNaN(new Date(value).getTime()), 'Data de start este obligatorie')
    .refine((value) => new Date(value).getTime() > Date.now(), 'Data de start trebuie sa fie in viitor'),
  leagueId: z.string().min(1, 'Liga este obligatorie'),
  homeTeamId: z.string().min(1, 'Echipa gazda este obligatorie'),
  awayTeamId: z.string().min(1, 'Echipa oaspete este obligatorie'),
}).refine((value) => value.homeTeamId !== value.awayTeamId, {
  message: 'Echipa gazda si echipa oaspete nu pot fi aceleasi',
  path: ['awayTeamId'],
})

export const stakeSchema = z.object({
  stake: z.coerce
    .number()
    .min(MIN_STAKE, `Miza minima este ${MIN_STAKE} RON`)
    .max(MAX_STAKE, `Miza maxima este ${MAX_STAKE} RON`),
})

