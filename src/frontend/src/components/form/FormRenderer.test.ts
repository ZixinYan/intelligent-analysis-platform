import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FormRenderer from './FormRenderer.vue'
import type { FieldCandidateSlotDTO, NodeConfigSchemaDTO } from '@/types/contract'

function createSchema(): NodeConfigSchemaDTO {
  return {
    schemaType: 'FORM',
    schemaVersion: '1.0.0',
    panelId: 'panel-1',
    sections: [
      {
        key: 'advanced',
        title: 'Advanced',
        order: 2,
        fields: [
          {
            field: 'picker',
            label: 'Picker',
            componentType: 'FIELD_PICKER',
            order: 2,
          },
          {
            field: 'threshold',
            label: 'Threshold',
            componentType: 'NUMBER_INPUT',
            order: 1,
            required: true,
            enableRules: [
              {
                watchField: 'mode',
                operator: 'EQ',
                targetValues: ['custom'],
                enabled: true,
              },
            ],
          },
        ],
      },
      {
        key: 'basic',
        title: 'Basic',
        order: 1,
        fields: [
          {
            field: 'mode',
            label: 'Mode',
            componentType: 'INPUT',
            order: 2,
          },
          {
            field: 'sql',
            label: 'SQL',
            componentType: 'SQL_EDITOR',
            order: 1,
            visibilityRules: [
              {
                watchField: 'mode',
                operator: 'EQ',
                targetValues: ['custom'],
                visible: true,
              },
            ],
          },
        ],
      },
      {
        key: 'mapping',
        title: 'Mapping',
        order: 3,
        fields: [
          {
            field: 'dimensions',
            label: 'Dimensions',
            componentType: 'FIELD_MULTI_SELECTOR',
            order: 1,
          },
        ],
      },
    ],
  }
}

const candidateSlots: FieldCandidateSlotDTO[] = [
  {
    slot: 'picker',
    required: true,
    acceptedTypes: ['STRING'],
    acceptedCapabilities: [],
    candidates: [
      { field: 'city', score: 0.9 },
      { field: 'dt', score: 0.8 },
    ],
  },
  {
    slot: 'dimensions',
    required: false,
    acceptedTypes: ['STRING'],
    acceptedCapabilities: [],
    candidates: [
      { field: 'province', score: 0.9 },
      { field: 'channel', score: 0.7 },
    ],
  },
]

describe('FormRenderer', () => {
  it('renders sections and fields by order', () => {
    const wrapper = mount(FormRenderer, {
      props: {
        schema: createSchema(),
        modelValue: { mode: 'custom', threshold: 10 },
        candidateSlots,
      },
    })

    const headers = wrapper.findAll('.form-renderer__header').map(item => item.text())
    expect(headers).toEqual(['Basic', 'Advanced', 'Mapping'])

    const labels = wrapper.findAll('.field-renderer__label span:first-child').map(item => item.text())
    expect(labels).toEqual(['SQL', 'Mode', 'Threshold', 'Picker', 'Dimensions'])
  })

  it('applies visibility rules, enable rules and validation state', async () => {
    const validEvents: boolean[] = []
    const wrapper = mount(FormRenderer, {
      props: {
        schema: createSchema(),
        modelValue: { mode: 'preset' },
      },
      attrs: {
        onValid: (value: boolean) => validEvents.push(value),
      },
    })

    expect(wrapper.text()).not.toContain('SQL')
    expect(validEvents.at(-1)).toBe(false)
    expect(wrapper.html()).toContain('Threshold不能为空')
    expect(wrapper.find('input[type="number"]').attributes('disabled')).toBeDefined()

    await wrapper.setProps({
      modelValue: {
        mode: 'custom',
        threshold: 12,
      },
    })

    expect(wrapper.text()).toContain('SQL')
    expect(validEvents.at(-1)).toBe(true)
    expect(wrapper.find('input[type="number"]').attributes('disabled')).toBeUndefined()
  })

  it('passes candidate slots to picker fields and emits updated model value', async () => {
    const wrapper = mount(FormRenderer, {
      props: {
        schema: createSchema(),
        modelValue: { mode: 'custom', threshold: 8, dimensions: ['province'] },
        candidateSlots,
      },
    })

    const selectOptions = wrapper.findAll('select.field-picker__select option').map(item => item.text())
    expect(selectOptions).toEqual(['请选择字段', 'city', 'dt'])
    expect(wrapper.text()).toContain('候选字段：city, dt')
    expect(wrapper.text()).toContain('province')
    expect(wrapper.text()).toContain('channel')

    await wrapper.find('select.field-picker__select').setValue('dt')

    const updateEvents = wrapper.emitted('update:modelValue')
    expect(updateEvents).toBeTruthy()
    expect(updateEvents?.[0]?.[0]).toMatchObject({
      mode: 'custom',
      threshold: 8,
      dimensions: ['province'],
      picker: 'dt',
    })
  })
})
