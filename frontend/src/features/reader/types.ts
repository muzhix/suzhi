export interface OutlineNode {
  path: string
  label: string
  unitCount: number
  children: OutlineNode[]
  ceYear?: number | null
  ganzhi?: string | null
}

export interface OutlineResponse {
  nodes: OutlineNode[]
  unitCount: number
}

export interface HeadingRule {
  id: string
  pattern: string
  level?: number | null
  label?: string | null
  pathTemplate?: string[] | null
  tocKeyTemplate?: string | null
  nodeType?: string | null
  mergeNextYear?: boolean | null
  prefix?: boolean | null
  foldIntoParent?: boolean | null
  ceYearGroup?: number | null
  ganzhiGroup?: number | null
}

export interface StructureProfile {
  id: string
  name: string
  toc?: {
    present?: boolean | null
    linePattern?: string | null
    usage?: string | null
    dropLeadingCopy?: boolean | null
  } | null
  headings: HeadingRule[]
  alignment?: string | null
  paragraph?: string | null
  neighbor?: { noCrossJuan?: boolean | null; noCrossNian?: boolean | null } | null
  skipEduNodeTypes?: string[] | null
}

export interface StructureScheme {
  id: string
  name: string
  profile: StructureProfile
}

export interface PreviewUnit {
  path: string
  text: string
}

export interface PreviewResponse {
  acceptable: boolean
  unitCount: number
  headingCount: number
  tocLineCount: number
  unmatchedHeadings: string[]
  unmatchedVolumes: string[]
  warnings: string[]
  outline: OutlineNode[]
  units: PreviewUnit[]
}

/** Select 不能用空字符串当选项值，用这个表示不选方案。 */
export const NONE_STRUCTURE_SCHEME = 'none'

/**
 * 把结构方案 Select 的值收成真正的 schemeId。
 *
 * @param value Select 选中值
 */
export function schemeIdFromSelect(value: unknown): string {
  const id = typeof value === 'string' ? value : ''
  return !id || id === NONE_STRUCTURE_SCHEME ? '' : id
}

/**
 * 有方案时必须先拿到可接受的预览才能确认；无方案走 B0 空行切段。
 *
 * @param schemeId 结构方案
 * @param preview 最近一次预览
 */
export function canConfirmExtract(schemeId: string, preview: Pick<PreviewResponse, 'acceptable'> | null): boolean {
  return !schemeId || Boolean(preview?.acceptable)
}

/**
 * 当前节点及其子孙的段落。
 *
 * @param units 预览段落
 * @param path 选中 path
 */
export function unitsForPath(units: PreviewUnit[], path: string): PreviewUnit[] {
  if (!path) {
    return []
  }
  return units.filter((unit) => unit.path === path || unit.path.startsWith(`${path}/`))
}

export interface TextUnit {
  id: string
  seq: number
  path: string
  displayText: string
}

export interface EduItem {
  id: string
  text: string
  status: string
  locationPrecision: string
  sources: { textUnitId: string; quote?: string; precision: string; purpose?: string }[]
}

export function firstLeaf(nodes: OutlineNode[]): string | null {
  if (!nodes.length) {
    return null
  }
  return firstLeafOf(nodes[0])
}

export function firstLeafOf(node: OutlineNode): string {
  if (!node.children?.length) {
    return node.path
  }
  return firstLeafOf(node.children[0])
}
