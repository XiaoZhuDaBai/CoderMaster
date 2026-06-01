<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Navbar from '@/components/Navbar.vue'
import { api } from '@/api'
import { getToken } from '@/api'
import { useUserStore } from '@/stores/user'
import deleteIcon from '@/assets/icons/delete.svg'
import aiAssistantIcon from '../../svg/ait_ai助手-copy.svg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const problem = ref(null)
const code = ref('')
const language = ref('java')
const isSubmitting = ref(false)
const isRunning = ref(false)
const submissionResult = ref(null)
const runResult = ref(null)
const showRunPanel = ref(true)
// 底部区域（自定义输入 / 测试结果）当前激活的 tab，模仿 LeetCode 风格
const activeBottomTab = ref('input') // 'input' | 'result'
const userInput = ref('')
const showAiMonitorModal = ref(false)
const isAiMonitorOn = ref(false)
const aiMonitorInfo = ref(null)
const showAiPanel = ref(false)
const showResultModal = ref(false)
const isSubmissionPolling = ref(false)
const submissionPollingTimer = ref(null)
const isRunPolling = ref(false)
const runPollingTimer = ref(null)

// 流式生成题解相关
const isGeneratingSolution = ref(false)
const solutionStreamContent = ref('') // 累积的流式文本
let solutionAbortController = null
let solutionReader = null
const solutionHtml = ref('')
const solutionPrompt = ref('')
// 对话消息列表：{ id, role: 'user'|'assistant', content, html, time }
const messages = ref([])

const nowTime = () => {
  const d = new Date()
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const escapeHtml = (unsafe) =>
  unsafe
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')

// 当流内容变化时，尝试用 marked 渲染为 HTML（动态加载 marked），失败则展示预格式化文本
watch(
  () => solutionStreamContent.value,
  async (val) => {
    if (!val) {
      solutionHtml.value = ''
      return
    }
    try {
      const mod = await import('marked')
      const md = mod?.marked ?? mod?.default ?? mod
      if (md && typeof md.parse === 'function') {
        solutionHtml.value = md.parse(val)
      } else if (typeof md === 'function') {
        solutionHtml.value = md(val)
      } else {
        solutionHtml.value = `<pre>${escapeHtml(val)}</pre>`
      }
    } catch (e) {
      // fallback to escaped pre
      solutionHtml.value = `<pre>${escapeHtml(val)}</pre>`
    }
  }
)

// Markdown 渲染：为题目/输入/输出生成 HTML（动态导入 marked）
const descriptionHtml = ref('')
const inputDescHtml = ref('')
const outputDescHtml = ref('')

const renderMarkdown = async (text) => {
  const raw = text || ''
  try {
    const mod = await import('marked')
    const md = mod?.marked ?? mod?.default ?? mod
    if (md && typeof md.parse === 'function') {
      return md.parse(raw)
    } else if (typeof md === 'function') {
      return md(raw)
    }
  } catch (e) {
    // ignore and fallback to escaped pre
  }
  return `<pre>${escapeHtml(raw)}</pre>`
}

const updateAllMarkdown = async () => {
  // description 优先使用 description 字段，否则使用 content
  const descSource = problem.value?.description ?? problem.value?.content ?? ''
  descriptionHtml.value = descSource ? await renderMarkdown(descSource) : ''
  inputDescHtml.value = problem.value?.inputDesc ? await renderMarkdown(problem.value.inputDesc) : ''
  outputDescHtml.value = problem.value?.outputDesc ? await renderMarkdown(problem.value.outputDesc) : ''
}

// 当 problem 改变时，重新渲染 markdown
watch(
  () => problem.value,
  async () => {
    await updateAllMarkdown()
  },
  { deep: true }
)

// 可调整大小相关
const leftPanel = ref(null)
const customInputSection = ref(null)
let isResizing = false
let isResizingInput = false

// AI 面板拖动与缩放相关
const aiPanel = ref(null)
let isDraggingAiPanel = false
let dragOffsetX = 0
let dragOffsetY = 0
let isResizingAiPanel = false
let resizeStartX = 0
let resizeStartY = 0
let startWidth = 0
let startHeight = 0

// Monaco Editor 相关
const editorContainer = ref(null)
let editor = null

const languageOptions = [
  { label: 'Java', value: 'java' },
  { label: 'C++', value: 'cpp' },
  { label: 'C', value: 'c' },
  { label: 'Python 3', value: 'python3' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'Go', value: 'go' },
]

// 切换 AI 监控（预留接口）
const toggleAiMonitoring = async () => {
  // 切换开关状态
  isAiMonitorOn.value = !isAiMonitorOn.value

  // 打开时提示一次（后续可在这里对接后端开启监控接口）
  if (isAiMonitorOn.value) {
    // TODO: 后续对接后端接口，例如：
    // const res = await api.startAiMonitor({ questionId: problem.value?.questionId, language: language.value })
    // aiMonitorInfo.value = res.data
    aiMonitorInfo.value = {
      title: 'AI 监控已开启',
      message: '系统将对你的解题过程进行智能分析，并在后续提供个性化提示和建议。实际内容将由后端接口返回。',
    }
    showAiMonitorModal.value = true
  }
}

// 在新窗口打开本地运行的 chat-view（端口 9510）
const toggleAiPanel = () => {
  window.open('http://localhost:9510/', '_blank')
}

// 停止流式生成
const stopSolutionStreaming = async () => {
  try {
    if (solutionAbortController) {
      solutionAbortController.abort()
    }
  } catch (e) {
    // ignore
  } finally {
    isGeneratingSolution.value = false
    solutionAbortController = null
    solutionReader = null
  }
}

// 解析 SSE chunk（可能包含多行），并将 token 追加到指定 assistant 消息
const handleSSEChunk = (chunk, assistantMsg) => {
  const lines = chunk.split('\n')
  for (const line of lines) {
    if (!line) continue
    if (line.startsWith('data: ')) {
      const data = line.substring(6)
      if (data === '[DONE]') {
        isGeneratingSolution.value = false
        return 'DONE'
      }
      // 将 token 追加到 assistantMsg.content
      assistantMsg.content = (assistantMsg.content || '') + data
    }
  }
  return null
}

// 发起流式生成请求，实时追加到 solutionStreamContent
const generateSolutionStreaming = async () => {
  if (!userStore.isLoggedIn) {
    window.alert('请先登录以使用题解生成功能')
    return
  }
  if (isGeneratingSolution.value) return

  // 基本请求体参考 API 文档
  const userUuid = String(userStore.userId || userStore.userInfo?.userId || '')
  if (!userUuid) {
    window.alert('无法获取用户信息，请重新登录')
    return
  }

  const requestBody = {
    request: {
      tagIds: problem.value?.tagNames || [],
      difficulty: getDifficultyText(problem.value?.difficulty) || '中等',
      source: problem.value?.source || '练习',
      questionType: problem.value?.questionType ?? 0,
      userUuid,
    },
    problem: {
      title: problem.value?.title || '',
      description: problem.value?.description || problem.value?.content || '',
      inputDesc: problem.value?.inputDesc || '',
      outputDesc: problem.value?.outputDesc || '',
      examples: problem.value?.examples || '',
      difficulty: problem.value?.difficulty ?? 1,
      questionType: problem.value?.questionType ?? 0,
      timeLimit: problem.value?.timeLimit,
      memoryLimit: problem.value?.memoryLimit,
    },
  }

  // create assistant message placeholder in messages
  const assistantMsg = {
    id: Date.now(),
    role: 'assistant',
    content: '',
    html: '',
    time: nowTime(),
  }
  messages.value.push({
    id: Date.now() + 1,
    role: 'user',
    content: prompt || solutionPrompt.value || '',
    time: nowTime(),
  })
  messages.value.push(assistantMsg)
  solutionStreamContent.value = ''
  isGeneratingSolution.value = true

  // 使用 fetch 发送 POST 请求并读取流
  solutionAbortController = new AbortController()
  try {
    const resp = await fetch('/api/solution/generate/streaming', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify(requestBody),
      signal: solutionAbortController.signal,
    })

    if (!resp.ok || !resp.body) {
      throw new Error(`请求失败: ${resp.status}`)
    }

    const reader = resp.body.getReader()
    solutionReader = reader
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      const status = handleSSEChunk(chunk, assistantMsg)
      // update assistant html via marked dynamically
      try {
        const mod = await import('marked')
        const md = mod?.marked ?? mod?.default ?? mod
        if (md && typeof md.parse === 'function') {
          assistantMsg.html = md.parse(assistantMsg.content)
        } else if (typeof md === 'function') {
          assistantMsg.html = md(assistantMsg.content)
        } else {
          assistantMsg.html = `<pre>${escapeHtml(assistantMsg.content)}</pre>`
        }
      } catch (e) {
        assistantMsg.html = `<pre>${escapeHtml(assistantMsg.content)}</pre>`
      }
      // ensure UI scrolls to bottom
      await nextTick()
      const sc = document.querySelector('.ai-messages')
      if (sc) sc.scrollTop = sc.scrollHeight
      if (status === 'DONE') break
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      // 用户主动取消
      solutionStreamContent.value += '\n\n[已取消]\n'
    } else {
      console.error('流式生成题解错误:', error)
      solutionStreamContent.value += `\n\n[生成失败：${error.message}]\n`
    }
  } finally {
    isGeneratingSolution.value = false
    try {
      if (solutionReader) {
        await solutionReader.cancel()
      }
    } catch (e) {}
    solutionAbortController = null
    solutionReader = null
  }
}

// 开始拖动 AI 面板
const startAiPanelDrag = (e) => {
  const panel = aiPanel.value
  if (!panel) return

  isDraggingAiPanel = true

  const rect = panel.getBoundingClientRect()
  // 将定位改为基于 top/left，便于拖动
  panel.style.right = 'auto'
  panel.style.bottom = 'auto'
  if (!panel.style.left && !panel.style.top) {
    panel.style.left = `${rect.left}px`
    panel.style.top = `${rect.top}px`
  }

  dragOffsetX = e.clientX - rect.left
  dragOffsetY = e.clientY - rect.top

  document.addEventListener('mousemove', handleAiPanelDrag)
  document.addEventListener('mouseup', stopAiPanelDrag)
}

const handleAiPanelDrag = (e) => {
  if (!isDraggingAiPanel) return
  const panel = aiPanel.value
  if (!panel) return

  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight

  let newLeft = e.clientX - dragOffsetX
  let newTop = e.clientY - dragOffsetY

  const rect = panel.getBoundingClientRect()
  const maxLeft = viewportWidth - rect.width
  const maxTop = viewportHeight - rect.height

  newLeft = Math.max(0, Math.min(maxLeft, newLeft))
  newTop = Math.max(0, Math.min(maxTop, newTop))

  panel.style.left = `${newLeft}px`
  panel.style.top = `${newTop}px`
}

const stopAiPanelDrag = () => {
  isDraggingAiPanel = false
  document.removeEventListener('mousemove', handleAiPanelDrag)
  document.removeEventListener('mouseup', stopAiPanelDrag)
}

// 开始缩放 AI 面板
const startAiPanelResize = (e) => {
  const panel = aiPanel.value
  if (!panel) return

  isResizingAiPanel = true

  const rect = panel.getBoundingClientRect()
  resizeStartX = e.clientX
  resizeStartY = e.clientY
  startWidth = rect.width
  startHeight = rect.height

  document.addEventListener('mousemove', handleAiPanelResize)
  document.addEventListener('mouseup', stopAiPanelResize)
  e.preventDefault()
}

const handleAiPanelResize = (e) => {
  if (!isResizingAiPanel) return
  const panel = aiPanel.value
  if (!panel) return

  const deltaX = e.clientX - resizeStartX
  const deltaY = e.clientY - resizeStartY

  const minWidth = 260
  const minHeight = 220
  const maxWidth = window.innerWidth * 0.95
  const maxHeight = window.innerHeight * 0.9

  let newWidth = startWidth + deltaX
  let newHeight = startHeight + deltaY

  newWidth = Math.max(minWidth, Math.min(maxWidth, newWidth))
  newHeight = Math.max(minHeight, Math.min(maxHeight, newHeight))

  panel.style.width = `${newWidth}px`
  panel.style.height = `${newHeight}px`
}

const stopAiPanelResize = () => {
  isResizingAiPanel = false
  document.removeEventListener('mousemove', handleAiPanelResize)
  document.removeEventListener('mouseup', stopAiPanelResize)
}

// 代码模板（Main-main 模式）
const codeTemplates = {
  java: `import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // 读取输入
        // 在这里编写你的输入读取代码
        
        // 处理数据
        // 在这里编写你的算法代码
        
        // 输出结果
        // 在这里编写你的输出代码
        
        scanner.close();
    }
}`,
  cpp: `#include <iostream>
#include <vector>
using namespace std;

int main() {
    // 读取输入
    // 在这里编写你的输入读取代码
    
    // 处理数据
    // 在这里编写你的算法代码
    
    // 输出结果
    // 在这里编写你的输出代码
    
    return 0;
}`,
  c: `#include <stdio.h>
#include <stdlib.h>

int main() {
    // 读取输入
    // 在这里编写你的输入读取代码
    
    // 处理数据
    // 在这里编写你的算法代码
    
    // 输出结果
    // 在这里编写你的输出代码
    
    return 0;
}`,
  python3: `import sys

def main():
    # 读取输入
    # 在这里编写你的输入读取代码
    # data = sys.stdin.read().split()
    
    # 处理数据
    # 在这里编写你的算法代码
    
    # 输出结果
    # 在这里编写你的输出代码
    # print(result)

if __name__ == '__main__':
    main()`,
  javascript: `const readline = require('readline');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

let input = [];

rl.on('line', (line) => {
    input.push(line);
});

rl.on('close', () => {
    // 处理输入数据
    // 在这里编写你的输入处理代码
    
    // 处理数据
    // 在这里编写你的算法代码
    
    // 输出结果
    // 在这里编写你的输出代码
    // console.log(result);
    
    process.exit(0);
});`,
  go: `package main

import (
    "fmt"
    "bufio"
    "os"
)

func main() {
    reader := bufio.NewReader(os.Stdin)
    writer := bufio.NewWriter(os.Stdout)
    defer writer.Flush()
    
    // 读取输入
    // 在这里编写你的输入读取代码
    
    // 处理数据
    // 在这里编写你的算法代码
    
    // 输出结果
    // 在这里编写你的输出代码
    // fmt.Fprintf(writer, "%d\\n", result)
}`
}

// 解析示例
const examples = computed(() => {
  if (!problem.value?.examples) return []
  try {
    if (typeof problem.value.examples === 'string') {
      return JSON.parse(problem.value.examples)
    }
    return problem.value.examples
  } catch (e) {
    console.error('解析示例失败:', e)
    return []
  }
})

// 语言映射到 Monaco Editor 的语言ID
const languageMap = {
  java: 'java',
  cpp: 'cpp',
  c: 'c',
  python3: 'python',
  javascript: 'javascript',
  go: 'go',
}

// 初始化 Monaco Editor
const initEditor = async () => {
  await nextTick()
  if (!editorContainer.value) return

  // 动态导入 Monaco Editor
  const monaco = await import('monaco-editor')

  // 如果编辑器已存在，先销毁
  if (editor) {
    editor.dispose()
  }

  editor = monaco.editor.create(editorContainer.value, {
    value: code.value || codeTemplates[language.value] || codeTemplates.java,
    language: languageMap[language.value] || 'java',
    theme: 'vs',
    automaticLayout: true,
    fontSize: 14,
    lineNumbers: 'on',
    roundedSelection: false,
    scrollBeyondLastLine: false,
    readOnly: false,
    minimap: {
      enabled: true,
    },
    // 括号匹配和补全
    matchBrackets: 'always',
    autoClosingBrackets: 'always',
    autoClosingQuotes: 'always',
    autoIndent: 'full',
    formatOnPaste: true,
    formatOnType: true,
    // 代码折叠
    folding: true,
    foldingStrategy: 'indentation',
    // 智能提示
    quickSuggestions: true,
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: 'on',
    tabCompletion: 'on',
    wordBasedSuggestions: 'matchingDocuments',
    // 缩进
    tabSize: 4,
    insertSpaces: true,
    detectIndentation: false,
    // 其他
    renderWhitespace: 'selection',
    renderLineHighlight: 'all',
    cursorBlinking: 'smooth',
    cursorSmoothCaretAnimation: 'on',
  })

  // 监听内容变化
  editor.onDidChangeModelContent(() => {
    code.value = editor.getValue()
  })

  // 监听窗口大小变化，自动调整编辑器大小
  window.addEventListener('resize', () => {
    if (editor) {
      editor.layout()
    }
  })
}

// 加载题目信息
onMounted(async () => {
  // 从 sessionStorage 获取题目信息
  const problemStr = sessionStorage.getItem('currentProblem')
  if (problemStr) {
    try {
      problem.value = JSON.parse(problemStr)
      // 根据语言设置默认代码模板
      if (!code.value) {
        code.value = codeTemplates[language.value] || codeTemplates.java
      }
      // 初始化编辑器
      await initEditor()
      // 渲染题目/输入/输出的 Markdown 为 HTML
      await updateAllMarkdown()
    } catch (e) {
      console.error('解析题目信息失败:', e)
      router.push('/question-bank')
    }
  } else {
    // 如果没有题目信息，返回题库页面
    router.push('/question-bank')
  }
})

onUnmounted(() => {
  stopSubmissionPolling()
  stopRunPolling()
  stopResize()
  stopResizeInput()
  // 停止题解流（若存在）
  stopSolutionStreaming()
  // 销毁编辑器
  if (editor) {
    editor.dispose()
    editor = null
  }
})

// 检查代码是否为模板代码（允许一些小的改动，如注释、空行等）
const isTemplateCode = (code, templates) => {
  if (!code || !code.trim()) return true
  
  // 移除注释和多余空白，只比较代码结构
  const normalizeCode = (str) => {
    return str
      .replace(/\/\/.*$/gm, '') // 移除单行注释
      .replace(/\/\*[\s\S]*?\*\//g, '') // 移除多行注释
      .replace(/\s+/g, ' ') // 合并空白字符
      .trim()
  }
  
  const normalizedCode = normalizeCode(code)
  
  // 检查是否与任何模板匹配
  for (const template of Object.values(templates)) {
    const normalizedTemplate = normalizeCode(template)
    
    // 如果规范化后的代码与模板完全相同，认为是模板
    if (normalizedCode === normalizedTemplate) {
      return true
    }
    
    // 如果代码长度与模板相近（差异小于15%），且包含模板的关键开头部分，也认为是模板
    if (normalizedTemplate.length > 0) {
      const lengthDiff = Math.abs(normalizedCode.length - normalizedTemplate.length) / normalizedTemplate.length
      const keyPart = normalizedTemplate.substring(0, Math.min(100, normalizedTemplate.length))
      if (lengthDiff < 0.15 && normalizedCode.includes(keyPart)) {
        return true
      }
    }
  }
  
  return false
}

// 切换语言
const changeLanguage = async (lang) => {
  language.value = lang
  
  if (editor) {
    // 获取当前代码
    const currentCode = editor.getValue()
    const currentTemplate = codeTemplates[language.value]
    
    // 判断是否需要切换模板（使用改进的判断逻辑）
    const shouldUseTemplate = isTemplateCode(currentCode, codeTemplates) || !currentCode.trim()
    
    // 动态导入并更新编辑器语言
    const monaco = await import('monaco-editor')
    monaco.editor.setModelLanguage(editor.getModel(), languageMap[language.value] || 'java')
    
    // 如果应该使用模板，则更新为新语言的模板
    if (shouldUseTemplate) {
      editor.setValue(currentTemplate)
      code.value = currentTemplate
    } else {
      // 保留用户代码，只更新语言
      code.value = currentCode
    }
  } else {
    // 如果编辑器未初始化，更新 code
    const currentTemplate = codeTemplates[language.value]
    if (!code.value || code.value.trim() === '') {
      code.value = currentTemplate
    }
  }
}

// 可调整大小功能
const startResize = (e) => {
  isResizing = true
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

const handleResize = (e) => {
  if (!isResizing) return
  const container = document.querySelector('.resizable-container')
  if (!container) return
  const containerRect = container.getBoundingClientRect()
  const leftWidth = e.clientX - containerRect.left
  const newWidth = Math.max(300, Math.min(containerRect.width * 0.7, leftWidth))
  // 修复：leftPanel 是普通的 div ref，直接使用 leftPanel.value
  if (leftPanel.value) {
    leftPanel.value.style.flex = `0 0 ${newWidth}px`
  }
}

const stopResize = () => {
  isResizing = false
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
}

// 调整自定义输入区域高度
const startResizeInput = (e) => {
  isResizingInput = true
  document.addEventListener('mousemove', handleResizeInput)
  document.addEventListener('mouseup', stopResizeInput)
  e.preventDefault()
}

const handleResizeInput = (e) => {
  if (!isResizingInput) return
  const section = customInputSection.value
  if (!section) return
  
  const container = section.closest('.code-editor-section')
  if (!container) return
  
  const containerRect = container.getBoundingClientRect()
  const editorBody = container.querySelector('.editor-body')
  if (!editorBody) return
  
  // 计算鼠标位置相对于容器的位置
  const mouseY = e.clientY
  const containerBottom = containerRect.bottom
  const newHeight = containerBottom - mouseY
  
  // 限制最小和最大高度
  const minHeight = 100
  const maxHeight = containerRect.height * 0.6 // 最多占容器的60%
  const finalHeight = Math.max(minHeight, Math.min(maxHeight, newHeight))
  
  section.style.height = `${finalHeight}px`
  section.style.flexShrink = '0'
}

const stopResizeInput = () => {
  isResizingInput = false
  document.removeEventListener('mousemove', handleResizeInput)
  document.removeEventListener('mouseup', stopResizeInput)
}

// 轮询获取提交结果
const pollSubmissionResult = async (submissionId) => {
  if (isSubmissionPolling.value) return

  isSubmissionPolling.value = true
  let pollCount = 0
  const maxPollCount = 30 // 最多轮询30次
  const pollInterval = 2000 // 每2秒轮询一次

  const poll = async () => {
    try {
      const result = await api.getSubmissionResult(submissionId)
      if (result.status && result.data) {
        submissionResult.value = result.data
        const status = result.data.judgeStatus
        if (status && status !== 'PENDING' && status !== 'JUDGING') {
          stopSubmissionPolling()
          return
        }
      }
    } catch (error) {
      console.error('轮询提交结果失败:', error)
    }

    pollCount++
    if (pollCount >= maxPollCount) {
      stopSubmissionPolling()
    }
  }

  await poll()
  if (isSubmissionPolling.value) {
    submissionPollingTimer.value = setInterval(poll, pollInterval)
  }
}

const stopSubmissionPolling = () => {
  if (submissionPollingTimer.value) {
    clearInterval(submissionPollingTimer.value)
    submissionPollingTimer.value = null
  }
  isSubmissionPolling.value = false
}

const pendingRunStatuses = ['PENDING', 'RUNNING', 'JUDGING']

const normalizeRunStatusValue = (status) => {
  const upper = (status || '').toUpperCase()
  if (['SUCCESS', 'AC', 'ACCEPTED'].includes(upper)) return 'SUCCESS'
  if (['FAILED', 'FAIL', 'WA', 'WRONG_ANSWER'].includes(upper)) return 'FAILED'
  if (
    [
      'ERROR',
      'RUNTIME_ERROR',
      'RE',
      'SYSTEM_ERROR',
      'SE',
      'COMPILE_ERROR',
      'CE',
      'TIME_LIMIT_EXCEEDED',
      'TLE',
      'MEMORY_LIMIT_EXCEEDED',
      'MLE',
    ].includes(upper)
  ) {
    return 'ERROR'
  }
  if (pendingRunStatuses.includes(upper)) return upper
  return upper || 'PENDING'
}

const parseJudgeResult = (judgeResult) => {
  if (!judgeResult) return {}
  let parsed = judgeResult
  if (typeof judgeResult === 'string') {
    try {
      parsed = JSON.parse(judgeResult)
    } catch (error) {
      console.warn('解析 judgeResult 失败:', error)
      return {}
    }
  }

  const stdout = Array.isArray(parsed.outputList) ? parsed.outputList.join('\n') : parsed.stdout || ''
  const stderr = Array.isArray(parsed.errorMessages) ? parsed.errorMessages.join('\n') : parsed.stderr || ''

  return {
    stdout,
    stderr,
    judgeInfo: parsed.judgeInfo,
  }
}

// 轮询获取运行结果
const pollRunResult = async (requestId) => {
  if (!requestId || isRunPolling.value) return

  isRunPolling.value = true
  let pollCount = 0
  const maxPollCount = 20
  const pollInterval = 2000

  const poll = async () => {
    try {
      const result = await api.getRunResult(requestId)
      if (result.status && result.data) {
        const payload = result.data
        const rawStatus = payload.judgeStatus || payload.status || ''
        const normalizedStatus = normalizeRunStatusValue(rawStatus)
        const judgeParse = parseJudgeResult(payload.judgeResult)

        runResult.value = {
          requestId: payload.requestId || requestId,
          status: rawStatus || normalizedStatus,
          normalizedStatus,
          stdout: judgeParse.stdout || payload.output || '',
          stderr: payload.errorMessage || judgeParse.stderr || '',
          judgeInfo: judgeParse.judgeInfo || payload.judgeInfo || null,
          timeCost: payload.timeCost,
          memoryCost: payload.memoryCost,
        }

        if (!pendingRunStatuses.includes(normalizedStatus)) {
          stopRunPolling()
          return
        }
      }
    } catch (error) {
      console.error('轮询运行结果失败:', error)
    }

    pollCount++
    if (pollCount >= maxPollCount) {
      stopRunPolling()
    }
  }

  await poll()
  if (isRunPolling.value) {
    runPollingTimer.value = setInterval(poll, pollInterval)
  }
}

const stopRunPolling = () => {
  if (runPollingTimer.value) {
    clearInterval(runPollingTimer.value)
    runPollingTimer.value = null
  }
  isRunPolling.value = false
}

const closeRunToast = () => {
  stopRunPolling()
  runResult.value = null
  showRunPanel.value = false
}

// 运行测试用例
const handleRun = async () => {
  if (!userStore.isLoggedIn) {
    window.alert('请先登录！')
    return
  }

  // 从编辑器获取代码
  const currentCode = editor ? editor.getValue() : code.value

  if (!currentCode.trim()) {
    window.alert('请输入代码！')
    return
  }

  isRunning.value = true
  stopRunPolling()
  runResult.value = null

  try {
    const userId = userStore.userId || userStore.userInfo?.userId
    if (!userId) {
      throw new Error('无法获取用户ID')
    }

    const questionId = problem.value?.questionId ?? problem.value?.id
    if (!questionId) {
      throw new Error('当前题目信息缺失，无法运行')
    }

    const params = {
      userId: Number(userId),
      code: currentCode,
      language: language.value,
      questionId,
    }

    // 如果有用户输入，添加 userInput
    if (userInput.value.trim()) {
      params.userInput = userInput.value.trim()
    }

    if (problem.value?.contentHash) {
      params.contentHash = problem.value.contentHash
    }
    if (problem.value) {
      params.questionSnapshot = JSON.stringify(problem.value)
    }

    const result = await api.runTestCase(params)

    if (result.status) {
      const payload = result.data
      const requestId = typeof payload === 'string' ? payload : payload?.requestId

      if (!requestId) {
        throw new Error('运行任务创建失败，请稍后重试')
      }

      runResult.value = {
        requestId,
        status: 'PENDING',
        normalizedStatus: 'PENDING',
        message: '运行任务已提交，请稍候...',
      }
      activeBottomTab.value = 'result'
      pollRunResult(requestId)
    } else {
      throw new Error(result.message || '运行失败')
    }
  } catch (error) {
    console.error('运行测试用例失败:', error)
    window.alert(error.message || '运行失败，请稍后重试')
  } finally {
    isRunning.value = false
  }
}

// 提交代码
const handleSubmit = async () => {
  if (!userStore.isLoggedIn) {
    window.alert('请先登录！')
    return
  }

  // 从编辑器获取代码
  const currentCode = editor ? editor.getValue() : code.value

  if (!currentCode.trim()) {
    window.alert('请输入代码！')
    return
  }

  isSubmitting.value = true
  stopSubmissionPolling()
  submissionResult.value = null

  try {
    const userId = userStore.userId || userStore.userInfo?.userId
    if (!userId) {
      throw new Error('无法获取用户ID')
    }

    const params = {
      userId: Number(userId),
      code: currentCode,
      language: language.value,
    }

    // 添加题目相关信息
    if (problem.value?.questionId) {
      params.questionId = problem.value.questionId
    }
    if (problem.value?.contentHash) {
      params.contentHash = problem.value.contentHash
    }
    if (problem.value) {
      params.questionSnapshot = JSON.stringify(problem.value)
    }

    const result = await api.submitCode(params)

    if (result.status && result.data) {
      submissionResult.value = result.data
      showResultModal.value = true
      // 开始轮询获取判题结果
      if (result.data.submissionId) {
        pollSubmissionResult(result.data.submissionId)
      }
    } else {
      throw new Error(result.message || '提交失败')
    }
  } catch (error) {
    console.error('提交代码失败:', error)
    window.alert(error.message || '提交失败，请稍后重试')
  } finally {
    isSubmitting.value = false
  }
}

// 关闭结果模态框
const closeResultModal = () => {
  showResultModal.value = false
  stopSubmissionPolling()
}

// 获取难度文本
const getDifficultyText = (difficulty) => {
  const map = { 0: '简单', 1: '中等', 2: '困难' }
  return map[difficulty] || '中等'
}

// 获取难度样式类
const getDifficultyClass = (difficulty) => {
  const map = { 0: 'tag-basic', 1: 'tag-intermediate', 2: 'tag-advanced' }
  return map[difficulty] || 'tag-intermediate'
}

// 获取状态文本
const getStatusText = (status) => {
  const map = {
    PENDING: '等待判题',
    JUDGING: '判题中',
    ACCEPTED: '通过',
    REJECTED: '不通过',
    TIME_LIMIT_EXCEEDED: '超时',
    MEMORY_LIMIT_EXCEEDED: '内存超限',
    COMPILE_ERROR: '编译错误',
    RUNTIME_ERROR: '运行时错误',
  }
  return map[status] || status
}

// 获取状态样式类
const getStatusClass = (status) => {
  const map = {
    ACCEPTED: 'status-accepted',
    REJECTED: 'status-rejected',
    TIME_LIMIT_EXCEEDED: 'status-error',
    MEMORY_LIMIT_EXCEEDED: 'status-error',
    COMPILE_ERROR: 'status-error',
    RUNTIME_ERROR: 'status-error',
    PENDING: 'status-pending',
    JUDGING: 'status-pending',
  }
  return map[status] || ''
}

// 获取运行状态文本
const getRunStatusText = (status) => {
  const upper = (status || '').toUpperCase()
  const map = {
    SUCCESS: '运行成功',
    AC: '运行通过',
    ACCEPTED: '运行通过',
    FAILED: '运行失败',
    FAIL: '运行失败',
    WA: '答案错误',
    WRONG_ANSWER: '答案错误',
    ERROR: '运行异常',
    RUNTIME_ERROR: '运行时错误',
    RE: '运行时错误',
    SYSTEM_ERROR: '系统错误',
    SE: '系统错误',
    COMPILE_ERROR: '编译错误',
    CE: '编译错误',
    TIME_LIMIT_EXCEEDED: '超出时间限制',
    TLE: '超出时间限制',
    MEMORY_LIMIT_EXCEEDED: '超出内存限制',
    MLE: '超出内存限制',
    RUNNING: '运行中',
    JUDGING: '运行中',
    PENDING: '等待运行',
  }
  return map[upper] || status || '运行中'
}

// 获取运行结果样式
const getRunStatusClass = (status) => {
  const normalized = normalizeRunStatusValue(status)
  if (normalized === 'SUCCESS') return 'run-toast-success'
  if (normalized === 'FAILED' || normalized === 'ERROR') return 'run-toast-error'
  return 'run-toast-pending'
}

// 格式化内存（KB -> MB）
const formatMemory = (kb) => {
  if (kb === undefined || kb === null) return '-'
  if (kb < 1024) return `${kb} KB`
  return `${(kb / 1024).toFixed(2)} MB`
}

// 获取语言显示文本
const getLanguageText = (lang) => {
  const map = {
    java: 'Java',
    cpp: 'C++',
    c: 'C',
    python3: 'Python 3',
    javascript: 'JavaScript',
    go: 'Go',
  }
  return map[lang] || lang
}

// 解析提交结果中的 judgeResult
const parsedJudgeResult = computed(() => {
  if (!submissionResult.value?.judgeResult) return null
  try {
    const parsed = JSON.parse(submissionResult.value.judgeResult)
    return parsed
  } catch (e) {
    console.error('解析 judgeResult 失败:', e)
    return null
  }
})
</script>

<template>
  <div class="problem-detail-page">
    <Navbar />

    <div v-if="problem" class="problem-detail-container">
      <div class="resizable-container">
        <!-- 左侧：题目信息区域 -->
        <div ref="leftPanel" class="problem-info">
          <div class="problem-header">
            <div class="problem-title-section">
              <h1>{{ problem.title || '未命名题目' }}</h1>
              <div class="problem-meta">
                <span class="tag" :class="getDifficultyClass(problem.difficulty)">
                  {{ getDifficultyText(problem.difficulty) }}
                </span>
                <span v-if="problem.source" class="problem-source">{{ problem.source }}</span>
                <span v-if="problem.timeLimit" class="problem-limit">
                  <i class="fas fa-clock"></i>
                  {{ problem.timeLimit }}ms
                </span>
                <span v-if="problem.memoryLimit" class="problem-limit">
                  <i class="fas fa-memory"></i>
                  {{ problem.memoryLimit }}MB
                </span>
              </div>
            </div>
            <button class="btn-back" @click="router.push('/question-bank')">
              <i class="fas fa-arrow-left"></i>
              返回题库
            </button>
          </div>

          <div class="problem-content">
            <!-- 题目描述 -->
            <div class="content-section">
              <h2>题目描述</h2>
              <div class="description-text">{{ problem.description || problem.content || '暂无描述' }}</div>
            </div>

            <!-- 输入描述 -->
            <div v-if="problem.inputDesc" class="content-section">
              <h2>输入描述</h2>
              <div class="description-text" v-html="inputDescHtml"></div>
            </div>

            <!-- 输出描述 -->
            <div v-if="problem.outputDesc" class="content-section">
              <h2>输出描述</h2>
              <div class="description-text" v-html="outputDescHtml"></div>
            </div>

            <!-- 示例 -->
            <div v-if="examples.length > 0" class="content-section">
              <h2>示例</h2>
              <div v-for="(example, index) in examples" :key="index" class="example-box">
                <div class="example-number">示例 {{ index + 1 }}</div>
                <div class="example-item example-input">
                  <div class="example-label">
                    <i class="fas fa-arrow-right"></i>
                    <strong>输入</strong>
                  </div>
                  <div class="example-code-wrapper">
                    <pre><code>{{ example.input }}</code></pre>
                  </div>
                </div>
                <div class="example-item example-output">
                  <div class="example-label">
                    <i class="fas fa-arrow-left"></i>
                    <strong>输出</strong>
                  </div>
                  <div class="example-code-wrapper">
                    <pre><code>{{ example.output }}</code></pre>
                  </div>
                </div>
                <div v-if="example.explanation" class="example-item example-explanation">
                  <div class="example-label">
                    <i class="fas fa-lightbulb"></i>
                    <strong>解释</strong>
                  </div>
                  <div class="explanation-text">{{ example.explanation }}</div>
                </div>
              </div>
            </div>

            <!-- 标签 -->
            <div v-if="problem.tagNames && problem.tagNames.length > 0" class="content-section">
              <h2>标签</h2>
              <div class="tags-list">
                <span v-for="tag in problem.tagNames" :key="tag" class="tag-item">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 调整大小手柄 -->
        <div class="resize-handle" @mousedown="startResize"></div>

        <!-- 右侧：代码编辑区域 -->
        <div class="right-panel">
          <div class="code-editor-section">
            <div class="editor-header">
              <div class="language-selector">
                <label>编程语言：</label>
                <select v-model="language" @change="changeLanguage(language)" class="language-select">
                  <option v-for="option in languageOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <div class="ai-monitor-toggle" @click="toggleAiMonitoring">
                  <span class="ai-monitor-label">AI 监控</span>
                  <div class="ai-switch" :class="{ 'ai-switch-on': isAiMonitorOn }">
                    <div class="ai-switch-knob"></div>
                  </div>
                </div>
              </div>
              <div class="editor-actions">
                <button class="btn-ai-panel" type="button" @click="toggleAiPanel">
                  <img :src="aiAssistantIcon" alt="AI 助手" class="ai-icon" />
                  AI 助手
                </button>
                <button class="btn-run" @click="handleRun" :disabled="isRunning || isSubmitting">
                  <i class="fas fa-play"></i>
                  {{ isRunning ? '运行中...' : '运行代码' }}
                </button>
                <button class="btn-submit" @click="handleSubmit" :disabled="isSubmitting || isRunning">
                  <i class="fas fa-paper-plane"></i>
                  {{ isSubmitting ? '提交中...' : '提交代码' }}
                </button>
              </div>
            </div>

            <div class="editor-body">
              <div ref="editorContainer" class="monaco-editor-container"></div>
            </div>

            <!-- 自定义输入（可选） / 测试结果（LeetCode 风格切换） -->
            <div class="custom-input-section" ref="customInputSection">
              <div class="input-resize-handle" @mousedown="startResizeInput">
                <i class="fas fa-grip-lines"></i>
              </div>
              <div class="input-header">
                <div class="bottom-tabs">
                  <button
                    type="button"
                    class="bottom-tab"
                    :class="{ 'bottom-tab-active': activeBottomTab === 'input' }"
                    @click="activeBottomTab = 'input'"
                  >
                    自定义输入
                  </button>
                  <button
                    type="button"
                    class="bottom-tab"
                    :class="{ 'bottom-tab-active': activeBottomTab === 'result' }"
                    @click="activeBottomTab = 'result'"
                  >
                    测试结果
                  </button>
                </div>
                <button
                  v-if="activeBottomTab === 'input'"
                  class="btn-clear"
                  type="button"
                  @click="userInput = ''"
                >
                  清空
                </button>
              </div>
              <div class="input-main">
                <!-- 自定义输入区域 -->
                <div v-show="activeBottomTab === 'input'" class="input-left">
                  <textarea
                    v-model="userInput"
                    class="custom-input"
                    placeholder="输入测试数据（可选）..."
                    rows="3"
                  ></textarea>
                </div>
                <!-- “测试结果”区域 -->
                <div v-show="activeBottomTab === 'result'" class="result-right">
                  <!-- 有运行结果时展示结果卡片 -->
                  <div
                    v-if="runResult && showRunPanel"
                    class="run-result-panel"
                    :class="getRunStatusClass(runResult.status)"
                  >
                    <div class="run-result-header">
                      <div>
                        <div class="run-result-title">运行结果</div>
                        <div class="run-result-status">
                          {{ getRunStatusText(runResult.status) }}
                        </div>
                      </div>
                      <button class="run-result-close" type="button" @click="closeRunToast">
                        <i class="fas fa-times"></i>
                      </button>
                    </div>
                    <div class="run-result-body">
                      <p class="run-result-id">请求ID: {{ runResult.requestId }}</p>
                      <p v-if="isRunPolling" class="run-result-progress">
                        <i class="fas fa-spinner fa-spin"></i>
                        正在获取运行结果...
                      </p>
                      <template v-else>
                        <div v-if="runResult.stdout" class="run-result-output">
                          <label>标准输出：</label>
                          <pre>{{ runResult.stdout }}</pre>
                        </div>
                        <div v-else-if="runResult.stderr" class="run-result-output">
                          <label>错误输出：</label>
                          <pre>{{ runResult.stderr }}</pre>
                        </div>
                        <p v-else class="run-result-empty">暂无输出</p>
                      </template>
                    </div>
                  </div>
                  <!-- 没有运行结果时的占位提示 -->
                  <div v-else class="run-result-placeholder">
                    <i class="fas fa-info-circle"></i>
                    <div class="placeholder-text">
                      <p>当前还没有运行结果。</p>
                      <p>请先在上方编辑代码并点击右上角“运行代码”，这里将展示最新一次运行的输出。</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    <!-- AI 监控提示模态框 -->
    <div v-if="showAiMonitorModal" class="ai-monitor-modal" @click.self="showAiMonitorModal = false">
      <div class="ai-monitor-modal-content">
        <button class="ai-monitor-close" @click="showAiMonitorModal = false">
          <img :src="deleteIcon" alt="关闭" class="icon-delete" />
        </button>
        <div class="ai-monitor-header">
          <h2>{{ (aiMonitorInfo && aiMonitorInfo.title) || 'AI 监控提示' }}</h2>
        </div>
        <div class="ai-monitor-body">
          <p class="ai-monitor-message">
            {{ (aiMonitorInfo && aiMonitorInfo.message) || 'AI 监控功能已启动，具体提示内容将在与后端对接后展示。' }}
          </p>
          <p class="ai-monitor-note">
            当前为前端占位实现，后续会根据后端返回的监控信息展示更丰富的建议与提示。
          </p>
        </div>
      </div>
    </div>

    <!-- AI 交互面板（可拖动、可调整大小） -->


    <!-- 提交结果模态框 -->
    <div v-if="showResultModal" class="result-modal" @click.self="closeResultModal">
      <div class="result-modal-content">
        <button class="close-btn" @click="closeResultModal">×</button>
        <div class="result-header">
          <h2>提交结果</h2>
        </div>
        <div v-if="submissionResult" class="result-body">
          <div class="result-item">
            <span class="result-label">提交ID：</span>
            <span class="result-value">{{ submissionResult.submissionId }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">题目ID：</span>
            <span class="result-value">{{ submissionResult.questionId }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">语言：</span>
            <span class="result-value">{{ getLanguageText(submissionResult.language) }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">状态：</span>
            <span class="result-value" :class="getStatusClass(submissionResult.judgeStatus)">
              {{ getStatusText(submissionResult.judgeStatus) }}
            </span>
          </div>
          <!-- 通过率展示 -->
          <div v-if="submissionResult.totalCases" class="result-item result-stat">
            <span class="result-label">通过情况：</span>
            <span class="result-value">
              <span :class="submissionResult.passedCases === submissionResult.totalCases ? 'stat-success' : submissionResult.passedCases > 0 ? 'stat-partial' : 'stat-fail'">
                {{ submissionResult.passedCases }} / {{ submissionResult.totalCases }}
              </span>
              <span class="stat-percent">({{ ((submissionResult.passedCases / submissionResult.totalCases) * 100).toFixed(1) }}%)</span>
            </span>
          </div>
          <!-- 耗时和内存 -->
          <div v-if="submissionResult.timeCost !== undefined" class="result-item">
            <span class="result-label">耗时：</span>
            <span class="result-value">{{ submissionResult.timeCost }}ms</span>
          </div>
          <div v-if="submissionResult.memoryCost !== undefined" class="result-item">
            <span class="result-label">内存：</span>
            <span class="result-value">{{ formatMemory(submissionResult.memoryCost) }}</span>
          </div>
          <!-- 解析 judgeResult 展示详细信息 -->
          <template v-if="parsedJudgeResult">
            <!-- 测试用例详情 -->
            <div v-if="parsedJudgeResult.judgeInfo" class="judge-info-section">
              <div class="judge-info-title">测试用例详情</div>
              <div class="judge-info-grid">
                <div class="judge-info-item">
                  <span class="ji-label">正确：</span>
                  <span class="ji-value">{{ parsedJudgeResult.judgeInfo.correct?.filter(c => c).length || 0 }}</span>
                </div>
                <div class="judge-info-item">
                  <span class="ji-label">错误：</span>
                  <span class="ji-value ji-error">{{ parsedJudgeResult.judgeInfo.correct?.filter(c => !c).length || 0 }}</span>
                </div>
                <div class="judge-info-item">
                  <span class="ji-label">执行时间：</span>
                  <span class="ji-value">{{ parsedJudgeResult.judgeInfo.time }}ms</span>
                </div>
                <div class="judge-info-item">
                  <span class="ji-label">内存：</span>
                  <span class="ji-value">{{ formatMemory(parsedJudgeResult.judgeInfo.memory) }}</span>
                </div>
              </div>
              <!-- 每个测试用例的通过状态 -->
              <div v-if="parsedJudgeResult.judgeInfo.correct && parsedJudgeResult.judgeInfo.correct.length > 0" class="test-cases-status">
                <span class="tc-label">测试用例：</span>
                <div class="tc-badges">
                  <span 
                    v-for="(isCorrect, idx) in parsedJudgeResult.judgeInfo.correct" 
                    :key="idx"
                    class="tc-badge"
                    :class="isCorrect ? 'tc-correct' : 'tc-wrong'"
                    :title="`测试用例 ${idx + 1}: ${isCorrect ? '通过' : '未通过'}`"
                  >
                    {{ idx + 1 }}
                  </span>
                </div>
              </div>
            </div>
            <!-- 错误信息 -->
            <div v-if="submissionResult.errorMessage" class="result-item error-message">
              <span class="result-label">错误信息：</span>
              <span class="result-value result-error-text">{{ submissionResult.errorMessage }}</span>
            </div>
            <!-- 标准输出（如果有） -->
            <div v-if="parsedJudgeResult.outputList && parsedJudgeResult.outputList.length > 0" class="output-section">
              <div class="output-title">输出内容：</div>
              <div class="output-list">
                <div v-for="(output, idx) in parsedJudgeResult.outputList" :key="idx" class="output-item">
                  <span class="output-index">#{{ idx + 1 }}</span>
                  <pre class="output-content">{{ output }}</pre>
                </div>
              </div>
            </div>
          </template>
          <div v-if="isSubmissionPolling" class="polling-indicator">
            <i class="fas fa-spinner fa-spin"></i>
            <span>正在获取判题结果...</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
</template>

<style scoped>
.problem-detail-page {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-light, #f5f5f5);
}

.problem-detail-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.resizable-container {
  display: flex;
  height: 100%;
  position: relative;
  flex: 1;
  min-height: 0;
}

.problem-info {
  flex: 0 0 50%;
  min-width: 300px;
  max-width: 70%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--white, #ffffff);
  border-right: 1px solid var(--border-color, #e0e0e0);
  overflow-y: auto;
}

.problem-header {
  padding: 1.5rem 2rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  background: var(--primary-light, #e8f0fe);
  flex-shrink: 0;
}

.problem-title-section h1 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-dark, #333);
  margin-bottom: 0.75rem;
}

.problem-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.problem-source {
  padding: 0.25rem 0.75rem;
  background: var(--bg-light, #f5f5f5);
  border-radius: 999px;
  font-size: 0.85rem;
  color: var(--text-dark, #333);
}

.problem-limit {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--text-light, #666);
}

.btn-back {
  padding: 0.6rem 1.2rem;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: var(--radius, 8px);
  background: var(--white, #ffffff);
  color: var(--text-dark, #333);
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  white-space: nowrap;
}

.btn-back:hover {
  background: var(--bg-light, #f5f5f5);
  border-color: var(--primary-color, #4361ee);
  color: var(--primary-color, #4361ee);
}

.problem-content {
  padding: 2rem;
  flex: 1;
}

.content-section {
  margin-bottom: 2rem;
}

.content-section:last-child {
  margin-bottom: 0;
}

.content-section h2 {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  margin-bottom: 1rem;
}

.description-text {
  color: var(--text-dark, #333);
  line-height: 1.8;
  white-space: pre-wrap;
}

.example-box {
  background: var(--bg-light, #f5f5f5);
  border-radius: var(--radius, 8px);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  border: 1px solid var(--border-color, #e0e0e0);
}

.example-box:last-child {
  margin-bottom: 0;
}

.example-number {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--primary-color, #4361ee);
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid var(--primary-color, #4361ee);
}

.example-item {
  margin-bottom: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.example-item:last-child {
  margin-bottom: 0;
}

.example-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-dark, #333);
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.example-label i {
  color: var(--primary-color, #4361ee);
  font-size: 0.85rem;
}

.example-label strong {
  color: var(--text-dark, #333);
  font-weight: 600;
}

.example-code-wrapper {
  width: 100%;
  overflow-x: auto;
}

.example-code-wrapper pre {
  margin: 0;
  padding: 1rem;
  background: var(--white, #ffffff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  font-family: 'Courier New', 'Consolas', 'Monaco', monospace;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--text-dark, #333);
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-x: auto;
}

.example-code-wrapper code {
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  background: transparent;
  padding: 0;
  border: none;
}

.example-explanation {
  margin-top: 0.5rem;
}

.explanation-text {
  padding: 0.75rem 1rem;
  background: var(--white, #ffffff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  color: var(--text-dark, #333);
  line-height: 1.6;
  font-size: 0.9rem;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.tag-item {
  padding: 0.3rem 0.8rem;
  background: var(--primary-light, #e8f0fe);
  color: var(--primary-color, #4361ee);
  border-radius: 999px;
  font-size: 0.85rem;
}

.resize-handle {
  width: 10px;
  background: var(--border-color, #e0e0e0);
  cursor: col-resize;
  transition: background 0.2s;
  flex-shrink: 0;
}

.resize-handle:hover {
  background: var(--primary-color, #4361ee);
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 300px;
  height: 100%;
  overflow: hidden;
}

.code-editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--white, #ffffff);
  min-height: 0;
}

.editor-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.language-selector {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.language-selector label {
  font-size: 0.9rem;
  color: var(--text-dark, #333);
  font-weight: 500;
}

.language-select {
  padding: 0.5rem 1rem;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: var(--radius, 8px);
  background: var(--white, #ffffff);
  color: var(--text-dark, #333);
  font-size: 0.9rem;
  cursor: pointer;
}

.ai-monitor-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-left: 1rem;
  cursor: pointer;
}

.ai-monitor-label {
  font-size: 0.8rem;
  color: var(--text-light, #666);
}

.ai-switch {
  width: 34px;
  height: 18px;
  border-radius: 999px;
  background: var(--border-color, #e0e0e0);
  position: relative;
  transition: background 0.2s;
}

.ai-switch-knob {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--white, #ffffff);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s;
}

.ai-switch.ai-switch-on {
  background: var(--primary-color, #4361ee);
}

.ai-switch.ai-switch-on .ai-switch-knob {
  transform: translateX(16px);
}

.editor-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-ai-monitor,
.btn-ai-panel {
  padding: 0.5rem 1rem;
  border-radius: var(--radius, 8px);
  border: 1px solid var(--border-color, #e0e0e0);
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-ai-monitor:hover:not(:disabled),
.btn-ai-panel:hover:not(:disabled) {
  background: var(--primary-light, #e8f0fe);
  border-color: var(--primary-color, #4361ee);
  color: var(--primary-color, #4361ee);
}

.btn-ai-monitor:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-run,
.btn-submit {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: var(--radius, 8px);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-run {
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
  border: 1px solid var(--border-color, #e0e0e0);
}

.btn-run:hover:not(:disabled) {
  background: var(--primary-light, #e8f0fe);
  border-color: var(--primary-color, #4361ee);
  color: var(--primary-color, #4361ee);
}

.btn-submit {
  background: var(--primary-color, #4361ee);
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background: #3857d8;
}

.btn-run:disabled,
.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.editor-body {
  flex: 1;
  min-height: 0;
  padding: 1rem;
  display: flex;
}

.monaco-editor-container {
  width: 100%;
  height: 100%;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: var(--radius, 8px);
  overflow: hidden;
}

.monaco-editor-container:focus-within {
  border-color: var(--primary-color, #4361ee);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.1);
}

.custom-input-section {
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color, #e0e0e0);
  flex-shrink: 0;
  position: relative;
  min-height: 160px;
  max-height: 60vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.input-resize-handle {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 8px;
  background: var(--border-color, #e0e0e0);
  cursor: row-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  z-index: 10;
}

.input-resize-handle:hover {
  background: var(--primary-color, #4361ee);
}

.input-resize-handle i {
  color: var(--text-light, #999);
  font-size: 0.7rem;
  opacity: 0.6;
}

.input-resize-handle:hover i {
  color: white;
  opacity: 1;
}

.input-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  margin-top: 0.5rem;
}

.input-header label {
  font-size: 0.9rem;
  color: var(--text-dark, #333);
  font-weight: 500;
}

.bottom-tabs {
  display: inline-flex;
  border-radius: 999px;
  background: var(--bg-light, #f5f5f5);
  padding: 2px;
  gap: 2px;
}

.bottom-tab {
  border: none;
  background: transparent;
  padding: 0.25rem 0.9rem;
  border-radius: 999px;
  font-size: 0.85rem;
  color: var(--text-light, #666);
  cursor: pointer;
  transition: all 0.15s ease;
}

.bottom-tab-active {
  background: var(--white, #ffffff);
  color: var(--primary-color, #4361ee);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
  font-weight: 600;
}

.bottom-tab:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-clear {
  padding: 0.25rem 0.75rem;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  background: var(--white, #ffffff);
  color: var(--text-light, #666);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-clear:hover {
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
}

.input-main {
  display: flex;
  gap: 1rem;
  height: 100%;
  min-height: 120px;
}

.input-left {
  flex: 1;
  display: flex;
}

.result-right {
  flex: 1;
  display: flex;
}

.custom-input {
  width: 100%;
  flex: 1;
  min-height: 60px;
  padding: 0.75rem;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: var(--radius, 8px);
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
  color: var(--text-dark, #333);
  background: var(--white, #ffffff);
  resize: none;
  outline: none;
  overflow-y: auto;
}

.custom-input:focus {
  border-color: var(--primary-color, #4361ee);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.1);
}

.run-result-panel {
  flex: 1;
  min-width: 0;
  background: var(--white, #ffffff);
  border-radius: var(--radius, 8px);
  border-left: 4px solid var(--primary-color, #4361ee);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
}

.run-result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.75rem 0.9rem 0.5rem;
  gap: 0.5rem;
}

.run-result-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-dark, #333);
}

.run-result-status {
  font-size: 0.8rem;
  color: var(--text-light, #666);
  margin-top: 0.1rem;
}

.run-result-close {
  border: none;
  background: transparent;
  color: var(--text-light, #888);
  cursor: pointer;
  font-size: 1rem;
  padding: 0.2rem;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.run-result-close:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-dark, #333);
}

.run-result-body {
  padding: 0 0.9rem 0.8rem;
  font-size: 0.85rem;
  color: var(--text-dark, #333);
}

.run-result-id {
  margin: 0 0 0.4rem;
  font-size: 0.78rem;
  color: var(--text-light, #777);
}

.run-result-progress {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-dark, #333);
  margin: 0;
}

.run-result-output {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.run-result-output label {
  font-size: 0.8rem;
  color: var(--text-light, #777);
}

.run-result-output pre {
  margin: 0;
  padding: 0.5rem;
  background: var(--bg-light, #f5f5f5);
  border-radius: 6px;
  border: 1px solid var(--border-color, #e0e0e0);
  font-family: 'Courier New', 'Consolas', 'Monaco', monospace;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.run-result-empty {
  margin: 0;
  color: var(--text-light, #777);
}

.run-result-placeholder {
  flex: 1;
  min-width: 0;
  border-radius: var(--radius, 8px);
  border: 1px dashed var(--border-color, #e0e0e0);
  background: var(--bg-light, #f5f5f5);
  padding: 0.9rem 1rem;
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  color: var(--text-light, #666);
  font-size: 0.85rem;
}

.run-result-placeholder i {
  font-size: 1rem;
  color: var(--primary-color, #4361ee);
  margin-top: 0.1rem;
}

.run-result-placeholder .placeholder-text p {
  margin: 0;
}

.run-result-placeholder .placeholder-text p + p {
  margin-top: 0.25rem;
}

.run-toast-success {
  border-left-color: #2ecc71;
}

.run-toast-error {
  border-left-color: #e74c3c;
}

.run-toast-pending {
  border-left-color: #f39c12;
}

/* 结果模态框样式 */
.result-modal {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.result-modal-content {
  background: var(--white, #ffffff);
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
  padding: 0;
  min-width: 400px;
  max-width: 600px;
  position: relative;
}

.close-btn {
  position: absolute;
  right: 16px;
  top: 16px;
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  z-index: 10;
  color: var(--text-light, #666);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
}

.result-header {
  padding: 1.5rem 2rem 1rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
}

.result-header h2 {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  margin: 0;
}

.result-body {
  padding: 1.5rem 2rem;
}

.result-item {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
}

.result-item:last-child {
  margin-bottom: 0;
}

.result-label {
  font-weight: 500;
  color: var(--text-dark, #333);
  margin-right: 0.5rem;
  min-width: 80px;
}

.result-value {
  color: var(--text-dark, #333);
}

.status-accepted {
  color: #2ecc71;
  font-weight: 600;
}

.status-rejected {
  color: #e74c3c;
  font-weight: 600;
}

.status-error {
  color: #e74c3c;
  font-weight: 600;
}

.status-pending {
  color: #f39c12;
  font-weight: 600;
}

.polling-indicator {
  margin-top: 1rem;
  padding: 0.75rem;
  background: var(--bg-light, #f5f5f5);
  border-radius: var(--radius, 8px);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-dark, #333);
  font-size: 0.9rem;
}

.polling-indicator i {
  color: var(--primary-color, #4361ee);
}

/* 提交结果增强样式 */
.result-stat .result-value {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.stat-success {
  color: #2ecc71;
  font-weight: 600;
}

.stat-partial {
  color: #f39c12;
  font-weight: 600;
}

.stat-fail {
  color: #e74c3c;
  font-weight: 600;
}

.stat-percent {
  color: var(--text-light, #777);
  font-size: 0.85rem;
}

.judge-info-section {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--bg-light, #f5f5f5);
  border-radius: var(--radius, 8px);
  border: 1px solid var(--border-color, #e0e0e0);
}

.judge-info-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
}

.judge-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}

.judge-info-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
}

.ji-label {
  color: var(--text-light, #666);
}

.ji-value {
  color: var(--text-dark, #333);
  font-weight: 500;
}

.ji-error {
  color: #e74c3c;
}

.test-cases-status {
  margin-top: 0.75rem;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.tc-label {
  font-size: 0.85rem;
  color: var(--text-light, #666);
  flex-shrink: 0;
}

.tc-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tc-badge {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  color: white;
}

.tc-correct {
  background: #2ecc71;
}

.tc-wrong {
  background: #e74c3c;
}

.error-message {
  margin-top: 1rem;
}

.result-error-text {
  color: #e74c3c;
  font-size: 0.85rem;
}

.output-section {
  margin-top: 1rem;
}

.output-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-dark, #333);
  margin-bottom: 0.5rem;
}

.output-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 200px;
  overflow-y: auto;
}

.output-item {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
}

.output-index {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: var(--text-light, #777);
  width: 30px;
}

.output-content {
  margin: 0;
  padding: 0.5rem;
  background: var(--white, #ffffff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  white-space: pre-wrap;
  word-break: break-word;
  flex: 1;
}

/* AI 监控模态框 */
.ai-monitor-modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2100;
}

.ai-monitor-modal-content {
  background: var(--white, #ffffff);
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
  width: 420px;
  max-width: 90%;
  position: relative;
  padding: 1.8rem 2rem 1.6rem;
}

.ai-monitor-close {
  position: absolute;
  right: 16px;
  top: 12px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-light, #666);
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.ai-monitor-close .icon-delete {
  width: 18px;
  height: 18px;
  display: block;
}

.ai-monitor-close:hover {
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
}

.ai-monitor-header h2 {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  margin: 0 0 0.75rem;
}

.ai-monitor-body {
  font-size: 0.9rem;
  color: var(--text-dark, #333);
  line-height: 1.7;
}

.ai-monitor-message {
  margin-bottom: 0.75rem;
}

.ai-monitor-note {
  font-size: 0.8rem;
  color: var(--text-light, #777);
}

/* AI 交互面板 */
.ai-panel {
  position: fixed;
  right: 20px;
  bottom: 80px;
  width: 380px;
  height: 480px;
  max-width: 90vw;
  max-height: 80vh;
  background: var(--white, #ffffff);
  border-radius: var(--radius, 10px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  flex-direction: column;
  z-index: 2050;
  overflow: hidden;
}

.ai-panel-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--primary-light, #e8f0fe);
}

.ai-panel-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-dark, #333);
}

.ai-panel-title i {
  color: var(--primary-color, #4361ee);
}

.ai-icon {
  width: 18px;
  height: 18px;
  display: inline-block;
}

.ai-panel-close {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-light, #666);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.ai-panel-close .icon-delete {
  width: 16px;
  height: 16px;
  display: block;
}

.ai-panel-close:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-dark, #333);
}

.ai-panel-body {
  flex: 1;
  overflow: auto;
  padding: 1rem;
  font-size: 0.9rem;
  color: var(--text-dark, #333);
}

.ai-panel-placeholder {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* AI 流式题解样式（局部样式） */
.ai-panel-controls {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.btn-generate-top {
  padding: 0.5rem 0.9rem;
  border-radius: 8px;
  background: var(--primary-color);
  color: white;
  border: none;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-stop-generate {
  padding: 0.5rem 0.9rem;
  border-radius: 8px;
  background: #ef4444;
  color: white;
  border: none;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.ai-stream-content {
  background: #0b1220;
  color: #e6eef8;
  border-radius: 8px;
  padding: 1rem;
  height: 100%;
  overflow: auto;
}

.ai-stream-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Courier New', monospace;
  font-size: 0.95rem;
  line-height: 1.6;
}

.ai-stream-markdown {
  color: #e6eef8;
}

/* LeetCode-like AI chat styles */
.ai-panel-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.ai-messages {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  scroll-behavior: smooth;
}

.ai-message {
  display: flex;
  width: 100%;
}

.ai-message-ai {
  justify-content: flex-start;
}

.ai-message-user {
  justify-content: flex-end;
}

.ai-message-bubble {
  max-width: 78%;
  padding: 12px 14px;
  border-radius: 10px;
  line-height: 1.5;
  font-size: 0.95rem;
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
  background: linear-gradient(180deg, rgba(10,20,40,0.95), rgba(10,20,40,0.9));
  color: #e6eef8;
  border: 1px solid rgba(255,255,255,0.04);
  word-break: break-word;
}

.ai-message-user .ai-message-bubble {
  background: var(--primary-color);
  color: white;
  border: none;
}

.ai-message-loading .ai-message-bubble {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(255,255,255,0.04);
}

.ai-chat-input {
  border-top: 1px solid rgba(255,255,255,0.03);
  padding: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  background: linear-gradient(180deg, rgba(255,255,255,0.01), transparent);
}

.ai-input {
  flex: 1;
  min-height: 44px;
  max-height: 160px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid rgba(0,0,0,0.06);
  background: rgba(255,255,255,0.03);
  color: #fff;
  resize: vertical;
  outline: none;
}

.ai-input::placeholder {
  color: rgba(230,238,248,0.5);
}

.ai-input-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-input-actions .btn-send,
.ai-input-actions .btn-stop,
.ai-input-actions .btn-clear {
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255,255,255,0.06);
  color: #fff;
  border: none;
  cursor: pointer;
  font-weight: 600;
}

.ai-input-actions .btn-send:disabled,
.ai-input-actions .btn-stop:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .ai-message-bubble {
    max-width: 88%;
    font-size: 0.95rem;
  }
  .ai-input-actions {
    flex-direction: row;
  }
}

.ai-panel-placeholder ul {
  padding-left: 1.2rem;
  margin: 0;
}

.ai-panel-placeholder li {
  margin-bottom: 0.35rem;
}

.ai-panel-tip {
  font-size: 0.8rem;
  color: var(--text-light, #777);
}

.ai-panel-resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 18px;
  height: 18px;
  cursor: se-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
}

.ai-panel-resize-handle span {
  width: 100%;
  height: 100%;
  border-right: 2px solid rgba(0, 0, 0, 0.15);
  border-bottom: 2px solid rgba(0, 0, 0, 0.15);
  border-radius: 0 0 10px 0;
}

@media (max-width: 1200px) {
  .resizable-container {
    flex-direction: column;
  }

  .problem-info {
    flex: 0 0 auto;
    max-width: 100%;
    max-height: 40%;
  }

  .resize-handle {
    width: 100%;
    height: 10px;
    cursor: row-resize;
  }

  .right-panel {
    flex: 1;
    min-height: 0;
  }
}

@media (max-width: 768px) {
  .problem-header {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-header {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-actions {
    width: 100%;
  }

  .btn-run,
  .btn-submit {
    flex: 1;
  }

  .run-toast {
    width: calc(100% - 40px);
    right: 20px;
    left: 20px;
  }

  .result-modal-content {
    min-width: 90%;
    margin: 20px;
  }
}
</style>
