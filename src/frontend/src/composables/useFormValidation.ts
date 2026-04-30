import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import type { ConditionOperator, PanelFieldDTO } from '@/types/contract'

function isEmptyValue(value: unknown) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

function compareValue(value: unknown, operator: ConditionOperator, targetValues: string[]) {
  const normalizedValue = Array.isArray(value) ? value.map(item => String(item)) : value
  switch (operator) {
    case 'EQ':
      return targetValues.includes(String(value))
    case 'NE':
      return !targetValues.includes(String(value))
    case 'IN':
      return Array.isArray(normalizedValue)
        ? normalizedValue.some(item => targetValues.includes(item))
        : targetValues.includes(String(value))
    case 'NOT_IN':
      return Array.isArray(normalizedValue)
        ? normalizedValue.every(item => !targetValues.includes(item))
        : !targetValues.includes(String(value))
    case 'GT':
      return Number(value) > Number(targetValues[0])
    case 'LT':
      return Number(value) < Number(targetValues[0])
    case 'CONTAINS':
      return Array.isArray(normalizedValue)
        ? normalizedValue.some(item => item.includes(String(targetValues[0] ?? '')))
        : String(value ?? '').includes(String(targetValues[0] ?? ''))
    case 'IS_EMPTY':
      return isEmptyValue(value)
    case 'IS_NOT_EMPTY':
      return !isEmptyValue(value)
    default:
      return false
  }
}

function collectValidationRules(field: PanelFieldDTO) {
  return [...(field.validations ?? []), ...(field.validation ? [field.validation] : [])]
}

export function useFieldState(field: PanelFieldDTO, model: MaybeRefOrGetter<Record<string, unknown>>) {
  const visible = computed(() => {
    if (field.visible === false) {
      return false
    }
    if (!field.visibilityRules?.length) {
      return true
    }
    const currentModel = toValue(model)
    return field.visibilityRules.every((rule) => {
      const matched = compareValue(currentModel[rule.watchField], rule.operator, rule.targetValues)
      return rule.visible ? matched : !matched
    })
  })

  const disabled = computed(() => {
    if (field.disabled) {
      return true
    }
    if (!field.enableRules?.length) {
      return false
    }
    const currentModel = toValue(model)
    return field.enableRules.some((rule) => {
      const matched = compareValue(currentModel[rule.watchField], rule.operator, rule.targetValues)
      return rule.enabled ? !matched : matched
    })
  })

  return {
    visible,
    disabled,
  }
}

export function validateFieldValue(field: PanelFieldDTO, value: unknown) {
  const errors: string[] = []
  const rules = collectValidationRules(field)
  if (field.required && isEmptyValue(value)) {
    errors.push(`${field.label}不能为空`)
  }
  for (const rule of rules) {
    if (rule.type === 'required' && isEmptyValue(value)) {
      errors.push(rule.message ?? `${field.label}不能为空`)
    }
    if (rule.type === 'min' && typeof value === 'number' && typeof rule.min === 'number' && value < rule.min) {
      errors.push(rule.message ?? `${field.label}不能小于${rule.min}`)
    }
    if (rule.type === 'max' && typeof value === 'number' && typeof rule.max === 'number' && value > rule.max) {
      errors.push(rule.message ?? `${field.label}不能大于${rule.max}`)
    }
    if (rule.type === 'minLength' && typeof value === 'string' && typeof rule.min === 'number' && value.length < rule.min) {
      errors.push(rule.message ?? `${field.label}长度不能少于${rule.min}`)
    }
    if (rule.type === 'maxLength' && typeof value === 'string' && typeof rule.maxLength === 'number' && value.length > rule.maxLength) {
      errors.push(rule.message ?? `${field.label}长度超限`)
    }
    if (rule.type === 'pattern' && typeof value === 'string' && typeof rule.message === 'string') {
      try {
        const pattern = String((rule as { pattern?: string }).pattern ?? '')
        if (pattern && !new RegExp(pattern).test(value)) {
          errors.push(rule.message)
        }
      }
      catch {
        errors.push(rule.message)
      }
    }
  }
  return errors
}
