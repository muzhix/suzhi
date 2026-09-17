export interface OutlineNode {
  path: string
  label: string
  unitCount: number
  children: OutlineNode[]
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

export interface PreviewResponse {
  acceptable: boolean
  unitCount: number
  headingCount: number
  tocLineCount: number
  unmatchedHeadings: string[]
  unmatchedVolumes: string[]
  warnings: string[]
  outline: OutlineNode[]
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
