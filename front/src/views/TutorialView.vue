<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'

const router = useRouter()
const tutorialMarkdown = ref(`# CodeMaster 使用教程

## 快速开始（1–2 分钟）
1. 打开首页，点击 “开始刷题” 进入练习/题库页面。  
2. 在题目列表选择一道题并打开后，阅读题目描述，开始解题。  
3. 如果需要 AI 帮助，打开页面的“AI 助手”聊天窗口，输入你的问题并发送即可。  
4. 聊天窗口支持流式返回，答案会逐步显示；也可以复制代码、点赞或给出反馈。

## 如何刷题（练习流程）
- 选择题库或难度：在题库或筛选面板中选择期望的题目类型（例如入门/中级/高级、算法/数据结构等）。  
- 做题与提交：在在线代码区实现并提交，提交后查看判题结果及系统给出的提示。  
- 标记与复习：遇到不会的题目可标记以便日后复习；建议把错题记录并在后续周期重做。  
- 建议节奏：每天 30–90 分钟集中刷题，先保证题目类型广度再做深度复盘。

## AI 助手使用指南
- 发送问题：在聊天输入框中直接输入问题或贴上代码片段，然后点击发送。  
- 普通流式模式（默认）：实时输出最终答案，适合快速获取代码或简要解答。  
- 深度思考模式（勾选）：模型先输出思维链（reasoning），随后输出最终答案（content），适合需要详细推理的场景。  
- 中途停止：使用“停止/取消”按钮可中断当前回答。  
- 复制与反馈：回答区支持复制与点赞/踩（反馈），便于保存或标记有用内容。  
- 提问小技巧：描述清晰、指定语言、给出示例输入输出、分步提问。

## 会话与历史管理
- 会话保存：首次发送会话时会生成会话ID并保存（可在侧边栏/会话列表查看）。  
- 查历史：进入会话列表查看历史消息并继续追问以保留上下文。  
- 删除会话：会话列表提供删除功能以清理记录。  
- 注意：未保存的临时会话在刷新后可能丢失，重要对话请先保存。

## 常见问题
- 答案显示为 Markdown 原文：界面支持渲染大部分 Markdown，但若未渲染可复制到本地编辑器查看。  
- 流式很慢或中断：检查网络/代理，重试或切换模式。  
- 深度思考输出很长：可折叠思维链，仅查看最终答案。

## 小技巧
- 提问越具体越好：说明输入规模、期望复杂度与语言。  
- 需要运行代码：明确语言与运行环境。  
- 分步提问：先要思路，再要伪代码，最后要完整实现.

如需返回首页，点击右上角“关闭”或使用浏览器后退。`)

const close = () => router.back()

const html = computed(() => marked.parse(tutorialMarkdown.value))
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6">
    <div class="bg-white w-full max-w-4xl max-h-[90vh] overflow-auto rounded-lg shadow-lg p-6">
      <div class="flex justify-between items-center mb-4">
        <h3 class="text-lg font-semibold">使用教程</h3>
        <button class="text-sm text-muted-foreground" @click="close">关闭</button>
      </div>
      <div class="prose max-w-none">
        <div v-html="html"></div>
      </div>
    </div>
  </div>
</template>


