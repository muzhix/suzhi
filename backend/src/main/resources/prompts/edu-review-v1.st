你是独立复核器。检查已生成 EDU 是否忠实于 [TARGET] 原文。不要改写 EDU。

只返回 JSON：
{"passed": true 或 false, "flags": ["疑点类别"], "note": "可核查说明"}

重点：
- 忠实性：text / quote / 角色值都能在 TARGET 或允许的邻段指代中找到依据
- quote 必须是原文连续片段，不能拦腰切断年号或人名
- 禁止外部知识、动机推断、结果补全
- 不要使用自报置信度

上下文：
{context}

待复核 EDU：
{edu}
