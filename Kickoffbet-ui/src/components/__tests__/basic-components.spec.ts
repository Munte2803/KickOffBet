import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import OddPill from '@/components/OddPill.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import TabNav from '@/components/TabNav.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import FormInput from '@/components/FormInput.vue'
import DateChoiceInput from '@/components/DateChoiceInput.vue'
import AppPagination from '@/components/AppPagination.vue'

describe('OddPill', () => {
  it('shows the label and odds with two decimals', () => {
    const wrapper = mount(OddPill, { props: { label: '1', odds: 1.5 } })
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('1.50')
  })

  it('emits click when active', async () => {
    const wrapper = mount(OddPill, { props: { label: 'X', odds: 3.2 } })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('stops click propagation so the parent card does not navigate', async () => {
    const parentClicks: Event[] = []
    const wrapper = mount({
      components: { OddPill },
      template: '<div @click="onParent"><OddPill label="1" :odds="2" /></div>',
      methods: { onParent: (event: Event) => parentClicks.push(event) },
    })
    await wrapper.find('button').trigger('click')
    expect(parentClicks).toHaveLength(0)
  })

  it('is disabled, unfocusable and silent when inactive', async () => {
    const wrapper = mount(OddPill, { props: { label: '2', odds: 4, inactive: true } })
    const button = wrapper.find('button')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('tabindex')).toBe('-1')
    expect(button.attributes('aria-disabled')).toBe('true')
    await button.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('marks the selected state visually', () => {
    const selected = mount(OddPill, { props: { label: '1', odds: 2, selected: true } })
    const plain = mount(OddPill, { props: { label: '1', odds: 2 } })
    expect(selected.classes()).toContain('bg-blue-600')
    expect(plain.classes()).not.toContain('bg-blue-600')
  })
})

describe('StatusBadge', () => {
  it.each([
    ['WON', 'Castigat', 'text-green-500'],
    ['LOST', 'Pierdut', 'text-red-500'],
    ['PENDING', 'In asteptare', 'text-yellow-500'],
    ['SCHEDULED', 'Programat', 'text-blue-500'],
    ['POSTPONED', 'Amanat', 'text-orange-500'],
    ['REFUND', 'Rambursare', 'text-purple-500'],
    ['CANCELLED', 'Anulat', 'text-subtle'],
  ])('renders %s as "%s" with the %s colour', (status, label, colour) => {
    const wrapper = mount(StatusBadge, { props: { status } })
    expect(wrapper.text()).toBe(label)
    expect(wrapper.classes()).toContain(colour)
  })

  it('pulses for live matches', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'LIVE' } })
    expect(wrapper.text()).toBe('In direct')
    expect(wrapper.classes()).toContain('animate-pulse')
  })

  it('falls back to the raw status and neutral colour for unknown values', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'MYSTERY' } })
    expect(wrapper.text()).toBe('MYSTERY')
    expect(wrapper.classes()).toContain('bg-gray-500/10')
  })
})

describe('TabNav', () => {
  const tabs = [
    { key: 'pending', label: 'In asteptare', count: 3 },
    { key: 'won', label: 'Castigate', count: 0 },
    { key: 'all', label: 'Toate' },
  ]

  it('renders one button per tab with optional counts (including zero)', () => {
    const wrapper = mount(TabNav, { props: { tabs, active: 'pending' } })
    const buttons = wrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['In asteptare (3)', 'Castigate (0)', 'Toate'])
  })

  it('highlights the active tab', () => {
    const wrapper = mount(TabNav, { props: { tabs, active: 'won' } })
    const buttons = wrapper.findAll('button')
    expect(buttons[1].classes()).toContain('border-blue-500')
    expect(buttons[0].classes()).not.toContain('border-blue-500')
  })

  it('emits change with the tab key', async () => {
    const wrapper = mount(TabNav, { props: { tabs, active: 'pending' } })
    await wrapper.findAll('button')[2].trigger('click')
    expect(wrapper.emitted('change')).toEqual([['all']])
  })
})

describe('AdminSortSelect', () => {
  const options = [
    { label: 'Implicit', value: '' },
    { label: 'Nume A-Z', value: 'name,asc' },
    { label: 'Nume Z-A', value: 'name,desc' },
  ]

  it('renders the default label and every option', () => {
    const wrapper = mount(AdminSortSelect, { props: { options } })
    expect(wrapper.text()).toContain('Sorteaza dupa')
    expect(wrapper.findAll('option').map((o) => o.text())).toEqual(['Implicit', 'Nume A-Z', 'Nume Z-A'])
  })

  it('uses a custom label and reflects the model value', () => {
    const wrapper = mount(AdminSortSelect, { props: { options, label: 'Ordine', modelValue: 'name,desc' } })
    expect(wrapper.text()).toContain('Ordine')
    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('name,desc')
  })

  it('emits update:modelValue when the selection changes', async () => {
    const wrapper = mount(AdminSortSelect, { props: { options, modelValue: '' } })
    await wrapper.find('select').setValue('name,asc')
    expect(wrapper.emitted('update:modelValue')).toEqual([['name,asc']])
  })
})

describe('FormInput', () => {
  it('renders label, required marker and value', () => {
    const wrapper = mount(FormInput, { props: { label: 'Email', required: true, modelValue: 'a@b.ro', type: 'email' } })
    expect(wrapper.find('label').text()).toBe('Email*')
    const input = wrapper.find('input')
    expect(input.attributes('type')).toBe('email')
    expect((input.element as HTMLInputElement).value).toBe('a@b.ro')
  })

  it('defaults to a text input', () => {
    const wrapper = mount(FormInput)
    expect(wrapper.find('input').attributes('type')).toBe('text')
    expect(wrapper.find('label').exists()).toBe(false)
  })

  it('emits update:modelValue on input', async () => {
    const wrapper = mount(FormInput, { props: { modelValue: '' } })
    await wrapper.find('input').setValue('salut')
    expect(wrapper.emitted('update:modelValue')).toEqual([['salut']])
  })

  it('shows the error message and error styling', () => {
    const wrapper = mount(FormInput, { props: { error: 'Emailul este obligatoriu' } })
    expect(wrapper.text()).toContain('Emailul este obligatoriu')
    expect(wrapper.find('input').classes()).toContain('border-red-500')
  })

  it('forwards extra attributes (e.g. autocomplete) and disabled state to the input', () => {
    const wrapper = mount(FormInput, { props: { disabled: true }, attrs: { autocomplete: 'email', name: 'email' } })
    const input = wrapper.find('input')
    expect(input.attributes('autocomplete')).toBe('email')
    expect(input.attributes('name')).toBe('email')
    expect(input.attributes('disabled')).toBeDefined()
  })

  it('uses the date picker component for date-like types', () => {
    const wrapper = mount(FormInput, { props: { type: 'date', modelValue: '2000-01-01', error: 'x' } })
    const picker = wrapper.findComponent(DateChoiceInput)
    expect(picker.exists()).toBe(true)
    expect(picker.props('modelValue')).toBe('2000-01-01')
    expect(picker.props('invalid')).toBe(true)
    expect(wrapper.find('input[type="date"]').exists()).toBe(false)
  })

  it('re-emits the date picker value', async () => {
    const wrapper = mount(FormInput, { props: { type: 'date', modelValue: '' } })
    wrapper.findComponent(DateChoiceInput).vm.$emit('update:modelValue', '1999-12-31')
    expect(wrapper.emitted('update:modelValue')).toEqual([['1999-12-31']])
  })
})

describe('AppPagination', () => {
  const pageLabels = (wrapper: ReturnType<typeof mount>) =>
    wrapper
      .findAll('button, span')
      .map((el) => el.text())
      .filter((text) => text !== 'Prev' && text !== 'Next' && text !== '')

  it('renders nothing for a single page without a total', () => {
    const wrapper = mount(AppPagination, { props: { page: 0, totalPages: 1 } })
    expect(wrapper.html()).not.toContain('button')
  })

  it('shows only the result count for a single non-empty page', () => {
    const wrapper = mount(AppPagination, { props: { page: 0, totalPages: 1, totalElements: 1 } })
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.text()).toBe('1 rezultat')
  })

  it('pluralises the result count', () => {
    const wrapper = mount(AppPagination, { props: { page: 0, totalPages: 3, totalElements: 25 } })
    expect(wrapper.text()).toContain('25 rezultate')
  })

  it('lists every page when there are at most 7', () => {
    const wrapper = mount(AppPagination, { props: { page: 2, totalPages: 7 } })
    expect(pageLabels(wrapper)).toEqual(['1', '2', '3', '4', '5', '6', '7'])
  })

  it('collapses distant pages with ellipses around the current page', () => {
    const wrapper = mount(AppPagination, { props: { page: 5, totalPages: 12 } })
    expect(pageLabels(wrapper)).toEqual(['1', '...', '5', '6', '7', '...', '12'])
  })

  it('has no leading ellipsis near the start and no trailing one near the end', () => {
    expect(pageLabels(mount(AppPagination, { props: { page: 0, totalPages: 10 } }))).toEqual(['1', '2', '...', '10'])
    expect(pageLabels(mount(AppPagination, { props: { page: 9, totalPages: 10 } }))).toEqual(['1', '...', '9', '10'])
  })

  it('highlights the current page', () => {
    const wrapper = mount(AppPagination, { props: { page: 1, totalPages: 3 } })
    const current = wrapper.findAll('button').find((b) => b.text() === '2')!
    expect(current.classes()).toContain('bg-blue-600')
  })

  it('disables Prev on the first page and Next on the last page', () => {
    const first = mount(AppPagination, { props: { page: 0, totalPages: 3 } })
    expect(first.findAll('button').at(0)!.attributes('disabled')).toBeDefined()
    expect(first.findAll('button').at(-1)!.attributes('disabled')).toBeUndefined()

    const last = mount(AppPagination, { props: { page: 2, totalPages: 3 } })
    expect(last.findAll('button').at(-1)!.attributes('disabled')).toBeDefined()
  })

  it('emits zero-based page changes from Prev, Next and page buttons', async () => {
    const wrapper = mount(AppPagination, { props: { page: 1, totalPages: 3 } })
    const buttons = wrapper.findAll('button')
    await buttons.at(0)!.trigger('click') // Prev
    await buttons.at(-1)!.trigger('click') // Next
    await buttons.find((b) => b.text() === '3')!.trigger('click')
    expect(wrapper.emitted('change')).toEqual([[0], [2], [2]])
  })

  it('does not emit when clicking the current page', async () => {
    const wrapper = mount(AppPagination, { props: { page: 1, totalPages: 3 } })
    await wrapper.findAll('button').find((b) => b.text() === '2')!.trigger('click')
    expect(wrapper.emitted('change')).toBeUndefined()
  })
})
