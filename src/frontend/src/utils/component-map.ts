import type { Component } from 'vue'
import InputField from '@/components/form/fields/InputField.vue'
import NumberField from '@/components/form/fields/NumberField.vue'
import SelectField from '@/components/form/fields/SelectField.vue'
import SwitchField from '@/components/form/fields/SwitchField.vue'
import SqlEditorField from '@/components/form/fields/SqlEditorField.vue'
import CodeEditorField from '@/components/form/fields/CodeEditorField.vue'
import FieldPickerField from '@/components/form/fields/FieldPickerField.vue'
import VariableBindingField from '@/components/form/fields/VariableBindingField.vue'
import DatasourceSelectField from '@/components/form/fields/DatasourceSelectField.vue'
import type { FieldComponentType } from '@/types/contract'

const componentMap: Record<FieldComponentType, Component> = {
  INPUT: InputField,
  TEXTAREA: InputField,
  SELECT: SelectField,
  MULTI_SELECT: SelectField,
  SWITCH: SwitchField,
  NUMBER_INPUT: NumberField,
  SQL_EDITOR: SqlEditorField,
  CODE_EDITOR: CodeEditorField,
  FIELD_PICKER: FieldPickerField,
  FIELD_MULTI_SELECTOR: FieldPickerField,
  DATASOURCE_SELECT: DatasourceSelectField,
}

export function resolveFieldComponent(componentType: FieldComponentType) {
  return componentMap[componentType] ?? VariableBindingField
}
