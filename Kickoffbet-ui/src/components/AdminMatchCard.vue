<script setup lang="ts">
import { useRouter } from 'vue-router'
import Panel from '@/components/Panel.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import { formatDateShort } from '@/utils/date.utils'
import type { MatchList } from '@/types/match.types'
import { translateActiveLabel } from '@/utils/labels.utils'

const props = defineProps<{
  match: MatchList
}>()

const router = useRouter()

const goToDetail = () => router.push({ name: 'admin-match-detail', params: { id: props.match.id } })
const goToTeam = (id: string) => router.push({ name: 'team', params: { id } })
const goToLeague = () => router.push({ name: 'league', params: { code: props.match.leagueCode } })

const getTeamTag = (name: string, tla?: string | null) => {
  if (tla?.trim()) return tla.trim().toUpperCase()

  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}
</script>

<template>
  <Panel class="group relative flex cursor-pointer flex-col text-center" @click="goToDetail">
    <div class="mb-2 flex items-center justify-between gap-3">
      <button
        v-if="match.leagueCode"
        class="flex min-w-0 items-center gap-1.5 text-[12px] text-muted transition-colors hover:text-blue-500"
        @click.stop="goToLeague"
      >
        <LogoFrame v-if="match.leagueLogo" :src="match.leagueLogo" size="cardLeague" />
        <span class="truncate">{{ match.leagueName }}</span>
      </button>

      <div class="flex items-center gap-2">
        <StatusBadge :status="match.status" />
        <span class="rounded-full border px-2 py-0.5 text-[10px] font-semibold" :class="match.active ? 'border-green-500/30 bg-green-500/10 text-green-500' : 'border-line bg-surface text-muted'">
          {{ translateActiveLabel(match.active) }}
        </span>
      </div>
    </div>

    <div class="px-2 py-1.5">
      <div class="mb-2 flex items-center justify-center gap-3">
        <button class="logo-hover-blue inline-flex items-center justify-center p-0.5" @click.stop="goToTeam(match.homeTeamId)">
          <LogoFrame v-if="match.homeTeamLogo" :src="match.homeTeamLogo" size="lg" />
          <span v-else class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-line-strong bg-surface text-[10px] text-subtle">Gazde</span>
        </button>

        <span class="text-[10px] font-bold text-subtle">vs</span>

        <button class="logo-hover-blue inline-flex items-center justify-center p-0.5" @click.stop="goToTeam(match.awayTeamId)">
          <LogoFrame v-if="match.awayTeamLogo" :src="match.awayTeamLogo" size="lg" />
          <span v-else class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-line-strong bg-surface text-[10px] text-subtle">Oaspeti</span>
        </button>
      </div>

      <div class="flex flex-col items-center">
        <div class="flex items-center justify-center gap-3">
          <button class="min-w-[44px] text-center text-base font-black tracking-[0.14em] text-fg transition-colors group-hover:text-blue-500 hover:underline" @click.stop="goToTeam(match.homeTeamId)">
            {{ getTeamTag(match.homeTeamName, match.homeTeamTla) }}
          </button>
          <span class="text-[10px] font-semibold uppercase tracking-[0.2em] text-subtle">vs</span>
          <button class="min-w-[44px] text-center text-base font-black tracking-[0.14em] text-fg transition-colors group-hover:text-blue-500 hover:underline" @click.stop="goToTeam(match.awayTeamId)">
            {{ getTeamTag(match.awayTeamName, match.awayTeamTla) }}
          </button>
        </div>

        <p class="mt-1 text-[10px] text-muted">{{ formatDateShort(match.startTime) }}</p>
      </div>
    </div>

    <div v-if="match.ftHome !== null && match.ftAway !== null" class="mb-2 text-center">
      <span class="rounded border border-line bg-surface-2 px-3 py-0.5 font-mono font-bold text-fg">
        {{ match.ftHome }} - {{ match.ftAway }}
      </span>
    </div>

    <div class="mt-auto border-t border-line pt-3 text-left">
      <p class="text-xs uppercase tracking-[0.2em] text-subtle">Actiune admin</p>
      <p class="mt-1 text-sm font-semibold text-fg">Deschide detaliile si editarile meciului</p>
    </div>
  </Panel>
</template>
