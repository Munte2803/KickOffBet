<script setup lang="ts">
import { useRouter } from 'vue-router'
import Panel from '@/components/Panel.vue'
import OddPill from '@/components/OddPill.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import { formatDateShort } from '@/utils/date.utils'
import { getTeamTag } from '@/utils/team.utils'
import type { MatchList } from '@/types/match.types'
import { useTicketStore } from '@/stores/ticket.store'
import { useTicketActions } from '@/composables/useTicketActions'

const props = withDefaults(defineProps<{
  match: MatchList
  linkLeague?: boolean
}>(), {
  linkLeague: true,
})

const router = useRouter()
const ticketStore = useTicketStore()
const { toggleOffer } = useTicketActions()

const goToMatch = () => router.push({ name: 'match-detail', params: { id: props.match.id } })
const goToTeam = (id: string) => router.push({ name: 'team', params: { id } })
const goToLeague = (code: string) => router.push({ name: 'league', params: { code } })

const handleOdd = (offer: NonNullable<typeof props.match.odd1>) => {
  toggleOffer(props.match, offer)
}


</script>

<template>
  <Panel
    no-hover
    class="group relative flex min-h-[148px] cursor-pointer flex-col border border-white/10 bg-black/40 p-1.5 text-center shadow-[0_0_0_1px_rgba(255,255,255,0.02)] transition-all duration-300 hover:border-blue-800 max-[390px]:min-h-[142px] max-[360px]:min-h-[138px] sm:min-h-[188px] sm:p-3 lg:min-h-[214px]"
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

      <StatusBadge :status="match.status" />
    </div>

    <div class="flex flex-1 flex-col justify-center px-0 py-0.5 sm:px-1 sm:py-1">
      <div class="mb-0.5 grid w-full grid-cols-[1fr_auto_1fr] items-center sm:mb-1.5">
        <button
          class="inline-flex items-center justify-center justify-self-end rounded-full p-0.5 transition-all hover:ring-2 hover:ring-blue-500"
          @click.stop="goToTeam(match.homeTeamId)"
        >
          <LogoFrame v-if="match.homeTeamLogo" :src="match.homeTeamLogo" size="card" />
          <span class="inline-flex h-6 w-6 items-center justify-center rounded-full border border-white/20 bg-white text-[8px] text-gray-600 sm:h-8 sm:w-8 sm:text-[9px] lg:h-10 lg:w-10" v-else>Gazde</span>
        </button>

        <div class="w-5 sm:w-6 lg:w-8" aria-hidden="true" />

        <button
          class="inline-flex items-center justify-center justify-self-start rounded-full p-0.5 transition-all hover:ring-2 hover:ring-blue-500"
          @click.stop="goToTeam(match.awayTeamId)"
        >
          <LogoFrame v-if="match.awayTeamLogo" :src="match.awayTeamLogo" size="card" />
          <span class="inline-flex h-6 w-6 items-center justify-center rounded-full border border-white/20 bg-white text-[8px] text-gray-600 sm:h-8 sm:w-8 sm:text-[9px] lg:h-10 lg:w-10" v-else>Oaspeti</span>
        </button>
      </div>

      <div class="flex flex-col items-center">
        <div class="grid w-full grid-cols-[1fr_auto_1fr] items-center gap-1 max-[390px]:gap-0.5 sm:gap-1.5">
          <button
            class="min-w-[24px] justify-self-end text-right text-[11px] font-black tracking-normal text-white transition-colors group-hover:text-blue-400 hover:underline sm:min-w-[38px] sm:text-[15px]"
            @click.stop="goToTeam(match.homeTeamId)"
          >
            {{ getTeamTag(match.homeTeamName, match.homeTeamTla) }}
          </button>
          <span class="px-0.5 text-center text-[8px] font-semibold tracking-[0.05em] text-gray-500 max-[390px]:text-[7px] sm:text-[10px] sm:tracking-[0.08em]">vs</span>
          <button
            class="min-w-[24px] justify-self-start text-left text-[11px] font-black tracking-normal text-white transition-colors group-hover:text-blue-400 hover:underline sm:min-w-[38px] sm:text-[15px]"
            @click.stop="goToTeam(match.awayTeamId)"
          >
            {{ getTeamTag(match.awayTeamName, match.awayTeamTla) }}
          </button>
        </div>

        <p class="mt-0.5 text-[8px] text-gray-400 max-[390px]:text-[7px] sm:mt-1 sm:text-[10px]">{{ formatDateShort(match.startTime) }}</p>
      </div>
    </div>

    <div v-if="match.ftHome !== null && match.ftAway !== null" class="mb-2 text-center">
      <span class="rounded border border-gray-700 bg-gray-800 px-1.5 py-0.5 font-mono text-[9px] font-bold text-white max-[390px]:px-1 max-[390px]:text-[8px] sm:px-3 sm:text-base">
        {{ match.ftHome }} - {{ match.ftAway }}
      </span>
    </div>

    <div
      v-if="match.odd1?.active || match.oddX?.active || match.odd2?.active"
      class="mt-auto flex min-h-[30px] items-end justify-center gap-0.5 pb-0 sm:min-h-[34px] sm:gap-1 sm:pb-0.5"
      @click.stop
    >
      <OddPill
        v-if="match.odd1?.active"
        label="1"
        :odds="match.odd1.odds"
        compact
        :selected="ticketStore.isSelected(match.odd1.id)"
        :inactive="!match.odd1.active"
        @click="handleOdd(match.odd1)"
      />
      <OddPill
        v-if="match.oddX?.active"
        label="X"
        :odds="match.oddX.odds"
        compact
        :selected="ticketStore.isSelected(match.oddX.id)"
        :inactive="!match.oddX.active"
        @click="handleOdd(match.oddX)"
      />
      <OddPill
        v-if="match.odd2?.active"
        label="2"
        :odds="match.odd2.odds"
        compact
        :selected="ticketStore.isSelected(match.odd2.id)"
        :inactive="!match.odd2.active"
        @click="handleOdd(match.odd2)"
      />
    </div>
  </Panel>
</template>
