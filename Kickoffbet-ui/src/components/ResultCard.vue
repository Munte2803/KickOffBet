<script setup lang="ts">
import { useRouter } from 'vue-router'
import Panel from '@/components/Panel.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import type { MatchList } from '@/types/match.types'
import { formatDateShort, formatTime } from '@/utils/date.utils'
import { getTeamTag } from '@/utils/team.utils'

const props = withDefaults(defineProps<{
  match: MatchList
  linkLeague?: boolean
}>(), {
  linkLeague: true,
})

const router = useRouter()

const goToTeam = (id: string) => router.push({ name: 'team', params: { id } })
const goToMatch = () => router.push({ name: 'match-detail', params: { id: props.match.id } })
const goToLeague = (code: string) => router.push({ name: 'league', params: { code } })


</script>

<template>
  <Panel
    no-hover
    class="group relative flex min-h-[142px] cursor-pointer flex-col border border-white/10 bg-black/40 p-1.5 text-center shadow-[0_0_0_1px_rgba(255,255,255,0.02)] transition-all duration-300 hover:border-blue-800 max-[390px]:min-h-[138px] max-[360px]:min-h-[134px] sm:min-h-[168px] sm:p-3 lg:min-h-[176px]"
    @click="goToMatch"
  >
    <div class="mb-0.5 flex min-h-[18px] items-center justify-between gap-1 sm:mb-1.5 sm:min-h-[22px] sm:gap-2">
      <button
        v-if="linkLeague && match.leagueCode"
        class="flex min-w-0 items-center gap-1 text-[9px] text-gray-400 transition-colors hover:text-blue-300 sm:gap-1.5 sm:text-[10px]"
        @click.stop="goToLeague(match.leagueCode)"
      >
        <LogoFrame v-if="match.leagueLogo" :src="match.leagueLogo" size="xs" />
        <span class="truncate">{{ match.leagueName }}</span>
      </button>

      <div v-else class="flex min-w-0 items-center gap-1 text-[9px] text-gray-400 sm:gap-1.5 sm:text-[10px]">
        <LogoFrame v-if="match.leagueLogo" :src="match.leagueLogo" size="xs" />
        <span class="truncate">{{ match.leagueName }}</span>
      </div>

      <span class="text-[9px] text-gray-500 sm:text-[10px]">{{ formatTime(match.startTime) }}</span>
    </div>

    <div class="flex flex-1 flex-col justify-center px-0 py-0.5 sm:px-1 sm:py-1">
      <div class="mb-1 grid w-full grid-cols-[1fr_auto_1fr] items-center sm:mb-2">
        <button
          class="inline-flex items-center justify-center justify-self-end rounded-full p-0.5 transition-all hover:ring-2 hover:ring-blue-500"
          @click.stop="goToTeam(match.homeTeamId)"
        >
          <LogoFrame v-if="match.homeTeamLogo" :src="match.homeTeamLogo" size="card" />
          <span v-else class="inline-flex h-6 w-6 items-center justify-center rounded-full border border-white/20 bg-white text-[8px] text-gray-600 sm:h-8 sm:w-8 sm:text-[9px] lg:h-10 lg:w-10">Gazde</span>
        </button>

        <div class="min-w-[46px] sm:min-w-[68px]" aria-hidden="true" />

        <button
          class="inline-flex items-center justify-center justify-self-start rounded-full p-0.5 transition-all hover:ring-2 hover:ring-blue-500"
          @click.stop="goToTeam(match.awayTeamId)"
        >
          <LogoFrame v-if="match.awayTeamLogo" :src="match.awayTeamLogo" size="card" />
          <span v-else class="inline-flex h-6 w-6 items-center justify-center rounded-full border border-white/20 bg-white text-[8px] text-gray-600 sm:h-8 sm:w-8 sm:text-[9px] lg:h-10 lg:w-10">Oaspeti</span>
        </button>
      </div>

      <div class="flex flex-col items-center">
        <div class="grid w-full grid-cols-[1fr_auto_1fr] items-center gap-1.5 max-[390px]:gap-1">
          <button
            class="truncate text-right text-[11px] font-black tracking-normal text-white transition-colors group-hover:text-blue-400 hover:underline sm:text-[15px]"
            @click.stop="goToTeam(match.homeTeamId)"
          >
            {{ getTeamTag(match.homeTeamName, match.homeTeamTla) }}
          </button>
          <div class="min-w-[40px] rounded border border-gray-700 bg-gray-800 px-0.5 py-0.75 font-mono text-[9px] font-bold text-white max-[390px]:min-w-[36px] max-[390px]:px-0.5 max-[390px]:py-0.5 max-[390px]:text-[8px] sm:min-w-[68px] sm:px-2 sm:py-1 sm:text-[15px]">
            {{ match.ftHome ?? '-' }} - {{ match.ftAway ?? '-' }}
          </div>
          <button
            class="truncate text-left text-[11px] font-black tracking-normal text-white transition-colors group-hover:text-blue-400 hover:underline sm:text-[15px]"
            @click.stop="goToTeam(match.awayTeamId)"
          >
            {{ getTeamTag(match.awayTeamName, match.awayTeamTla) }}
          </button>
        </div>

        <p class="mt-1 text-[8px] text-gray-400 max-[390px]:text-[7px] sm:mt-2 sm:text-[10px]">{{ formatDateShort(match.startTime) }}</p>
      </div>
    </div>
  </Panel>
</template>
