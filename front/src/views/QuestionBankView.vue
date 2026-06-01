<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Navbar from '@/components/Navbar.vue'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()

const userStore = useUserStore()
const isPanelCollapsed = ref(false)
const isPanelExpanded = ref(false)
const selectedTopics = ref([]) // 移除默认选中的标签，让用户自己选择
const selectedDifficulty = ref('') // 移除默认难度
const selectedScenario = ref('')
const showCards = ref(false)
const isLoading = ref(false)
const isPolling = ref(false) // 是否正在轮询
const additionalRequirements = ref('')
const questionNumber = ref(1)
const generatedProblems = ref([])
let pollingTimer = null // 轮询定时器

const difficultyOptions = [
  { label: '简单', value: '简单' },
  { label: '中等', value: '中等' },
  { label: '困难', value: '困难' },
]

const scenarioOptions = [
  { label: '竞赛', value: '竞赛' },
  { label: '面试', value: '面试' },
  { label: '练习', value: '练习' },
]

const optionGroups = [
  {
    title: '数据结构',
    options: [
      { label: '数组', value: '1' },
      { label: '字符串', value: '2' },
      { label: '链表', value: '3' },
      { label: '栈', value: '4' },
      { label: '队列', value: '5' },
      { label: '树', value: '6' },
      { label: '图', value: '7' },
      { label: '哈希表', value: '8' },
      { label: '堆', value: '9' },
      { label: '并查集', value: '10' },
      { label: '字典树', value: '11' },
      { label: '线段树', value: '12' },
      { label: '树状数组', value: '13' },
      { label: '前缀和', value: '14' },
      { label: '差分数组', value: '15' },
      { label: '平衡二叉树', value: '16' },
    ],
  },
  {
    title: '算法',
    options: [
      { label: '动态规划', value: '17' },
      { label: '贪心', value: '18' },
      { label: '回溯', value: '19' },
      { label: '分治', value: '20' },
      { label: '排序', value: '21' },
      { label: '搜索', value: '22' },
      { label: '深度优先搜索', value: '23' },
      { label: '广度优先搜索', value: '24' },
      { label: '双指针', value: '25' },
      { label: '滑动窗口', value: '26' },
      { label: '位运算', value: '27' },
      { label: '数学', value: '28' },
      { label: '模拟', value: '29' },
      { label: '二分查找', value: '30' },
      { label: '拓扑排序', value: '31' },
      { label: '最短路径', value: '32' },
      { label: '最小生成树', value: '33' },
      { label: '字符串匹配', value: '34' },
      { label: '数论', value: '35' },
      { label: '组合数学', value: '36' },
      { label: '几何', value: '37' },
      { label: '博弈论', value: '38' },
      { label: '构造', value: '39' },
      { label: '交互', value: '40' },
    ],
  },
]

const topicLabelMap = optionGroups.reduce((map, group) => {
  group.options.forEach((option) => {
    map[option.value] = option.label
  })
  return map
}, {})

const hasSelection = computed(() => selectedTopics.value.length > 0)

// 所有选中的标签（包括主题、难度、场景）
const allSelectedTags = computed(() => {
  const tags = []
  
  // 添加主题标签
  selectedTopics.value.forEach((value) => {
    tags.push({
      type: 'topic',
      value,
      label: topicLabelMap[value] ?? value,
    })
  })
  
  // 添加难度标签
  if (selectedDifficulty.value) {
    tags.push({
      type: 'difficulty',
      value: selectedDifficulty.value,
      label: selectedDifficulty.value,
    })
  }
  
  // 添加场景标签
  if (selectedScenario.value) {
    tags.push({
      type: 'scenario',
      value: selectedScenario.value,
      label: selectedScenario.value,
    })
  }
  
  return tags
})

// 保留原有的 selectedTags 用于兼容
const selectedTags = computed(() =>
  selectedTopics.value.map((value) => ({
    value,
    label: topicLabelMap[value] ?? value,
  }))
)

// 获取难度文本
const getDifficultyText = (difficulty) => {
  const map = { 0: '简单', 1: '中等', 2: '困难' }
  return map[difficulty] || '中等'
}

// 获取难度标签样式类
const getDifficultyTagClass = (difficulty) => {
  const map = { 0: 'tag-basic', 1: 'tag-intermediate', 2: 'tag-advanced' }
  return map[difficulty] || 'tag-intermediate'
}

// 获取简短描述（限制长度）
const getShortDescription = (description) => {
  if (!description) return '暂无描述'
  // 限制在150个字符以内
  if (description.length <= 150) return description
  return description.substring(0, 150) + '...'
}

// 获取标签类型样式类
const getTagTypeClass = (type) => {
  const map = {
    topic: '',
    difficulty: 'qb-tag-difficulty',
    scenario: 'qb-tag-scenario',
  }
  return map[type] || ''
}

const togglePanel = () => {
  isPanelCollapsed.value = !isPanelCollapsed.value
}

const togglePanelExpanded = () => {
  isPanelExpanded.value = !isPanelExpanded.value
}

const removeTopic = (value, event) => {
  if (event) {
    event.stopPropagation()
    event.preventDefault()
  }
  selectedTopics.value = selectedTopics.value.filter((topic) => topic !== value)
}

// 移除标签（支持主题、难度、场景）
const removeTag = (tag, event) => {
  if (event) {
    event.stopPropagation()
    event.preventDefault()
  }
  
  if (tag.type === 'topic') {
    selectedTopics.value = selectedTopics.value.filter((topic) => topic !== tag.value)
  } else if (tag.type === 'difficulty') {
    selectedDifficulty.value = ''
  } else if (tag.type === 'scenario') {
    selectedScenario.value = ''
  }
}

// 停止轮询
const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  isPolling.value = false
}

// 轮询获取新生成的题目
const pollNewProblems = async (userKey, expectedCount = null) => {
  if (isPolling.value) return

  isPolling.value = true
  let pollCount = 0
  const maxPollCount = 30 // 最多轮询30次（约1分钟）
  const pollInterval = 2000 // 每2秒轮询一次

  // 记录开始轮询时的题目数量，用于判断是否有新题目
  const initialCount = generatedProblems.value.length

  const poll = async () => {
    try {
      const result = await api.getNewProblems(userKey)

      console.log('轮询获取新题目结果:', result)

      if (result.status && result.data && Array.isArray(result.data)) {
        // 更新题目列表
        if (result.data.length > 0) {
          generatedProblems.value = result.data
          showCards.value = true
          isPanelExpanded.value = false
        }

        // 检查是否已经生成了足够数量的题目
        const currentTotal = generatedProblems.value.length
        const newProblemsCount = currentTotal - initialCount

        // 如果有期望数量且已达到，或者没有期望数量但有新题目，停止轮询
        if ((expectedCount && newProblemsCount >= expectedCount) ||
            (!expectedCount && result.data.length > 0)) {
          stopPolling()
          return
        }
      }

      pollCount++
      if (pollCount >= maxPollCount) {
        // 达到最大轮询次数，停止轮询
        stopPolling()
        // 显示当前状态
        const currentTotal = generatedProblems.value.length
        const newProblemsCount = currentTotal - initialCount
        if (expectedCount && newProblemsCount < expectedCount) {
          window.alert(`已生成 ${newProblemsCount}/${expectedCount} 道题目，生成过程可能仍在继续，请稍后刷新页面查看`)
        } else if (generatedProblems.value.length === initialCount) {
          window.alert('题目生成中，请稍后刷新页面查看')
        }
        return
      }
    } catch (error) {
      console.error('轮询获取题目失败:', error)
      // 轮询失败不中断，继续尝试
      pollCount++
      if (pollCount >= maxPollCount) {
        stopPolling()
      }
    }
  }

  // 立即执行一次
  await poll()

  // 设置定时轮询
  pollingTimer = setInterval(poll, pollInterval)
}

const handleGenerate = async () => {
  if (!hasSelection.value) {
    window.alert('请至少选择一个学习主题！')
    return
  }

  if (!userStore.isLoggedIn) {
    window.alert('请先登录！')
    return
  }

  if (!selectedDifficulty.value) {
    window.alert('请选择一个难度！')
    return
  }

  // 将tagIds转换为标签名称
  const tagNames = selectedTopics.value.map((value) => topicLabelMap[value] || value)

  // 获取难度和场景
  const difficulty = selectedDifficulty.value
  const source = selectedScenario.value || undefined

  // 获取用户UUID（使用 userId 作为 userKey）
  const userKey = String(userStore.userId || userStore.userInfo?.userId || '')

  if (!userKey) {
    window.alert('无法获取用户信息，请重新登录！')
    return
  }

  // 停止之前的轮询
  stopPolling()

  isLoading.value = true

  try {
    const params = {
      tagIds: tagNames,
      difficulty: difficulty,
      number: questionNumber.value,
      userUuid: userKey,
    }

    if (source) {
      params.source = source
    }

    if (additionalRequirements.value.trim()) {
      params.additionalRequirements = additionalRequirements.value.trim()
    }

    // 调用生成接口
    const result = await api.generateProblem(params)

    if (result.status) {
      // 生成请求成功，开始轮询获取新题目
      // 注意：不清空 generatedProblems，保留上一次的数据
      showCards.value = true
      isPanelExpanded.value = false
      
      // 开始轮询获取新题目，传入期望的数量
      await pollNewProblems(userKey, questionNumber.value)
    } else {
      window.alert(result.message || '生成题目失败，请稍后重试')
    }
  } catch (error) {
    console.error('生成题目失败:', error)
    window.alert(error.message || '生成题目失败，请稍后重试')
    stopPolling()
  } finally {
    isLoading.value = false
  }
}

// 加载新题目
const loadNewProblems = async () => {
  if (!userStore.isLoggedIn) {
    return
  }

  const userKey = String(userStore.userId || userStore.userInfo?.userId || '')
  if (!userKey) {
    return
  }

  try {
    const result = await api.getNewProblems(userKey)
    
    if (result.status && result.data && Array.isArray(result.data) && result.data.length > 0) {
      // 获取到新题目，更新列表
      generatedProblems.value = result.data
      showCards.value = true
    }
  } catch (error) {
    console.error('加载新题目失败:', error)
    // 静默失败，不影响用户体验
  }
}

onMounted(() => {
  // 如果用户已登录，自动加载新题目
  if (userStore.isLoggedIn) {
    loadNewProblems()
  } else {
    showCards.value = true
  }
})

// 开始做题
const startProblem = (problem) => {
  if (!problem) {
    window.alert('当前题目信息缺失，无法开始做题')
    return
  }

  // 将题目信息转换为 ProblemDetailView 需要的格式
  // 这里直接使用题库返回的原始数据，并补齐必要字段
  const problemData = {
    title: problem.title,
    description: problem.description || problem.content,
    content: problem.content || problem.description,
    difficulty: typeof problem.difficulty === 'number' ? problem.difficulty : 1,
    tagNames: problem.tagNames || [],
    // 统一用内容哈希作为主键，兼容不同字段
    contentHash: problem.contentHash || problem.id || problem.questionId,
    questionId: problem.questionId || problem.id || problem.contentHash,
    timeLimit: problem.timeLimit,
    memoryLimit: problem.memoryLimit,
    source: problem.source,
    createdAt: problem.createdAt || problem.createTime || problem.generatedAt,
    // 保留可能需要的字段
    inputDesc: problem.inputDesc,
    outputDesc: problem.outputDesc,
    examples: problem.examples,
    tagIds: problem.tagIds,
    generatedAt: problem.generatedAt,
    createTime: problem.createTime,
  }

  // 将题目信息存储到 sessionStorage，供做题页面使用
  sessionStorage.setItem('currentProblem', JSON.stringify(problemData))

  // 跳转到做题页面（优先使用 contentHash 作为路由参数）
  router.push({
    name: 'problem-detail',
    params: { id: problemData.contentHash || problemData.questionId || 'unknown' },
  })
}

// 重置所有选择
const resetAllSelections = () => {
  selectedTopics.value = []
  selectedDifficulty.value = ''
  selectedScenario.value = ''
  additionalRequirements.value = ''
  questionNumber.value = 1
}

// 组件卸载时清理轮询
onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div>
    <Navbar />

    <div class="question-bank">
      <div class="qb-layout" :class="{ 'panel-collapsed': isPanelCollapsed }">
        <aside class="qb-panel" :class="{ collapsed: isPanelCollapsed }">
          <div class="qb-panel-header">
            <div class="qb-panel-header-content">
              <div class="qb-panel-title-row">
                <h3>学习选项</h3>
                <button class="qb-icon-btn" type="button" @click="togglePanel">
                  <svg v-if="isPanelCollapsed" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                    <path d="M347.9 512L791.7 907.9l-57.9 51.7L232.3 512 733.8 64.4l57.9 51.7L347.9 512z" fill="currentColor"/>
                  </svg>
                  <svg v-else viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                    <path d="M676.1 512L232.3 116.1l57.9-51.7L791.7 512 290.2 959.6l-57.9-51.7L676.1 512z" fill="currentColor"/>
                  </svg>
                </button>
              </div>
              <p class="qb-panel-desc">选择你希望生成的题目类型</p>
              <div class="qb-panel-tips">
                <span class="qb-tip-item"><span class="qb-tip-num">1</span>点击"详细设置"可选择数据结构、算法、难度等</span>
                <span class="qb-tip-item"><span class="qb-tip-num">2</span>选中的标签会显示在这里，支持删除操作</span>
              </div>
            </div>
          </div>

          <div v-if="allSelectedTags.length" class="qb-selected-tags">
            <span v-for="tag in allSelectedTags" :key="`${tag.type}-${tag.value}`" class="qb-selected-tag" :class="getTagTypeClass(tag.type)">
              <span class="qb-tag-prefix" v-if="tag.type === 'difficulty'">难度：</span>
              <span class="qb-tag-prefix" v-if="tag.type === 'scenario'">场景：</span>
              {{ tag.label }}
              <button
                type="button"
                class="qb-tag-remove"
                aria-label="移除选项"
                @click.stop="removeTag(tag, $event)"
              >
                <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                  <path d="M983.722351 796.075891L701.304951 511.763073l284.312819-284.312818c51.176307-51.176307 51.176307-136.470153 0-189.541879-51.176307-51.176307-136.470153-51.176307-189.541879 0L511.763073 322.221194 227.450255 37.908376C176.273947-13.267932 90.980102-13.267932 37.908376 37.908376-13.267932 89.084683-13.267932 174.378528 37.908376 227.450255l284.312818 284.312818L37.908376 796.075891c-51.176307 51.176307-51.176307 136.470153 0 189.541879 51.176307 51.176307 136.470153 51.176307 189.541879 0l284.312818-284.312819 284.312818 284.312819c51.176307 51.176307 136.470153 51.176307 189.541879 0 51.176307-53.071726 51.176307-138.365571-1.895419-189.541879z" fill="currentColor"/>
                </svg>
              </button>
            </span>
          </div>

          <div v-else class="qb-selected-empty">点击"详细设置"选择主题</div>

          <div class="qb-quick-actions">
            <button class="qb-expand-btn" type="button" @click="togglePanelExpanded">
              <i class="fas fa-expand"></i>
              详细设置
            </button>
          </div>
        </aside>

        <section class="qb-content">
          <div v-if="!showCards" class="qb-empty-state">
            <i class="fas fa-lightbulb"></i>
            <h2>准备好开始学习之旅！</h2>
            <p>点击左侧"详细设置"选择你感兴趣的数据结构或算法主题，然后点击"生成学习内容"。</p>
          </div>

          <div v-else class="qb-card-grid">
            <!-- 显示轮询状态（只有在没有题目且正在轮询时显示） -->
            <div v-if="isPolling && generatedProblems.length === 0" class="qb-polling-state">
              <i class="fas fa-spinner fa-spin"></i>
              <p>正在生成题目，请稍候...</p>
            </div>
            
            <!-- 显示生成的题目 -->
            <template v-if="generatedProblems.length > 0">
              <article v-for="(problem, index) in generatedProblems" :key="problem.contentHash || index" class="qb-card">
                <header class="qb-card-header">
                  <span>{{ problem.title || `题目 ${index + 1}/${generatedProblems.length}` }}</span>
                  <span class="tag" :class="getDifficultyTagClass(problem.difficulty)">
                    {{ getDifficultyText(problem.difficulty) }}
                  </span>
                </header>
                <div class="qb-card-body">
                  <p class="qb-card-description">{{ getShortDescription(problem.description || problem.content) }}</p>
                  <div v-if="problem.tagNames && problem.tagNames.length > 0" class="qb-card-tags">
                    <span v-for="tag in problem.tagNames.slice(0, 3)" :key="tag" class="qb-card-tag">{{ tag }}</span>
                    <span v-if="problem.tagNames.length > 3" class="qb-card-tag-more">+{{ problem.tagNames.length - 3 }}</span>
                  </div>
                  <div class="qb-card-meta-info">
                    <span v-if="problem.timeLimit" class="qb-meta-item">
                      <i class="fas fa-clock"></i>
                      {{ problem.timeLimit }}ms
                    </span>
                    <span v-if="problem.memoryLimit" class="qb-meta-item">
                      <i class="fas fa-memory"></i>
                      {{ problem.memoryLimit }}MB
                    </span>
                    <span v-if="problem.source" class="qb-meta-item">
                      <i class="fas fa-tag"></i>
                      {{ problem.source }}
                    </span>
                  </div>
                </div>
                <footer class="qb-card-footer">
                  <div>
                    <p class="qb-card-label">AI生成</p>
                  </div>
                  <button type="button" class="btn btn-signup" @click="startProblem(problem)">开始做题</button>
                </footer>
              </article>
              <!-- 如果正在轮询，显示提示 -->
              <div v-if="isPolling" class="qb-polling-hint">
                <i class="fas fa-spinner fa-spin"></i>
                <span>正在获取最新题目...</span>
              </div>
            </template>
          </div>
        </section>
      </div>
    </div>

    <!-- 展开的详细设置模态框 -->
    <div v-if="isPanelExpanded" class="qb-expanded-modal" @click.self="togglePanelExpanded">
      <div class="qb-expanded-content">
        <div class="qb-expanded-header">
          <h2>详细设置</h2>
          <button class="qb-expanded-close" type="button" @click="togglePanelExpanded">
            <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
              <path d="M983.722351 796.075891L701.304951 511.763073l284.312819-284.312818c51.176307-51.176307 51.176307-136.470153 0-189.541879-51.176307-51.176307-136.470153-51.176307-189.541879 0L511.763073 322.221194 227.450255 37.908376C176.273947-13.267932 90.980102-13.267932 37.908376 37.908376-13.267932 89.084683-13.267932 174.378528 37.908376 227.450255l284.312818 284.312818L37.908376 796.075891c-51.176307 51.176307-51.176307 136.470153 0 189.541879 51.176307 51.176307 136.470153 51.176307 189.541879 0l284.312818-284.312819 284.312818 284.312819c51.176307 51.176307 136.470153 51.176307 189.541879 0 51.176307-53.071726 51.176307-138.365571-1.895419-189.541879z" fill="currentColor"/>
            </svg>
          </button>
        </div>

        <div class="qb-expanded-body">
          <div class="qb-expanded-section">
            <h3>已选择的标签</h3>
            <div v-if="allSelectedTags.length" class="qb-selected-tags-expanded">
              <span v-for="tag in allSelectedTags" :key="`${tag.type}-${tag.value}`" class="qb-selected-tag" :class="getTagTypeClass(tag.type)">
                <span class="qb-tag-prefix" v-if="tag.type === 'difficulty'">难度：</span>
                <span class="qb-tag-prefix" v-if="tag.type === 'scenario'">场景：</span>
                {{ tag.label }}
                <button
                  type="button"
                  class="qb-tag-remove"
                  aria-label="移除选项"
                  @click="removeTag(tag, $event)"
                >
                  <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                    <path d="M983.722351 796.075891L701.304951 511.763073l284.312819-284.312818c51.176307-51.176307 51.176307-136.470153 0-189.541879-51.176307-51.176307-136.470153-51.176307-189.541879 0L511.763073 322.221194 227.450255 37.908376C176.273947-13.267932 90.980102-13.267932 37.908376 37.908376-13.267932 89.084683-13.267932 174.378528 37.908376 227.450255l284.312818 284.312818L37.908376 796.075891c-51.176307 51.176307-51.176307 136.470153 0 189.541879 51.176307 51.176307 136.470153 51.176307 189.541879 0l284.312818-284.312819 284.312818 284.312819c51.176307 51.176307 136.470153 51.176307 189.541879 0 51.176307-53.071726 51.176307-138.365571-1.895419-189.541879z" fill="currentColor"/>
                  </svg>
                </button>
              </span>
            </div>
            <div v-else class="qb-selected-empty">暂无选择，请在下方选择选项</div>
          </div>

          <div class="qb-expanded-section">
            <h3>数据结构</h3>
            <div class="qb-option-list-expanded">
              <label
                v-for="option in optionGroups[0].options"
                :key="option.value"
                class="qb-option-item"
              >
                <input v-model="selectedTopics" type="checkbox" :value="option.value" />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>

          <div class="qb-expanded-section">
            <h3>算法</h3>
            <div class="qb-option-list-expanded">
              <label
                v-for="option in optionGroups[1].options"
                :key="option.value"
                class="qb-option-item"
              >
                <input v-model="selectedTopics" type="checkbox" :value="option.value" />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>

          <div class="qb-expanded-section">
            <h3>难度 <span class="required">*</span></h3>
            <div class="qb-option-list-expanded">
              <label
                v-for="option in difficultyOptions"
                :key="option.value"
                class="qb-option-item"
              >
                <input v-model="selectedDifficulty" type="radio" :value="option.value" name="difficulty-expanded" />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>

          <div class="qb-expanded-section">
            <h3>场景</h3>
            <div class="qb-option-list-expanded">
              <label
                v-for="option in scenarioOptions"
                :key="option.value"
                class="qb-option-item"
              >
                <input v-model="selectedScenario" type="radio" :value="option.value" name="scenario-expanded" />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>

          <div class="qb-expanded-section">
            <h3>生成数量（最多4个）</h3>
            <div class="qb-slider-container">
              <input
                v-model.number="questionNumber"
                type="range"
                min="1"
                max="4"
                class="qb-slider"
              />
              <span class="qb-slider-value">{{ questionNumber }}</span>
            </div>
          </div>

          <div class="qb-expanded-section">
            <h3>额外要求（可选）</h3>
            <textarea
              v-model="additionalRequirements"
              class="qb-textarea"
              placeholder="例如：需要用到滑动窗口、要求时间复杂度O(n)等"
              rows="3"
            ></textarea>
          </div>
        </div>

        <div class="qb-expanded-footer">
          <button class="btn btn-reset" type="button" @click="resetAllSelections">
            <i class="fas fa-redo"></i>
            重置
          </button>
          <div class="qb-footer-actions">
            <button class="btn btn-login" type="button" @click="togglePanelExpanded">取消</button>
            <button class="btn btn-signup" type="button" @click="handleGenerate" :disabled="isLoading">
              {{ isLoading ? '生成中...' : '生成题目' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 轮询状态样式 */
.qb-polling-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: var(--text-light);
}

.qb-polling-state i {
  font-size: 2.5rem;
  color: var(--primary-color);
  margin-bottom: 1rem;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.qb-polling-state p {
  font-size: 1rem;
  color: var(--text-dark);
}

/* 轮询提示（在已有题目时显示） */
.qb-polling-hint {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  background: var(--bg-light);
  border-radius: var(--radius);
  color: var(--text-light);
  font-size: 0.9rem;
}

.qb-polling-hint i {
  color: var(--primary-color);
}

/* 卡片内容区域 */
.qb-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.qb-card-description {
  color: var(--text-dark);
  line-height: 1.6;
  font-size: 0.9rem;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 卡片标签 */
.qb-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.qb-card-tag {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  background: var(--bg-light);
  color: var(--text-dark);
  font-size: 0.75rem;
  border: 1px solid var(--border-color);
}

.qb-card-tag-more {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  background: var(--primary-light);
  color: var(--primary-color);
  font-size: 0.75rem;
  font-weight: 500;
}

/* 卡片元信息 */
.qb-card-meta-info {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-color);
}

.qb-meta-item {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--text-light);
}

.qb-meta-item i {
  font-size: 0.75rem;
  width: 14px;
  text-align: center;
}

/* 标签前缀样式 */
.qb-tag-prefix {
  font-size: 0.75rem;
  color: var(--text-light);
  margin-right: 0.25rem;
}

/* 难度标签样式 */
.qb-tag-difficulty {
  background: rgba(67, 97, 238, 0.1);
  border: 1px solid rgba(67, 97, 238, 0.2);
}

/* 场景标签样式 */
.qb-tag-scenario {
  background: rgba(25, 167, 116, 0.1);
  border: 1px solid rgba(25, 167, 116, 0.2);
}

/* 详细设置模态框 footer 样式 */
.qb-expanded-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem 2rem;
  border-top: 1px solid var(--border-color, #e0e0e0);
  background: var(--bg-light, #f5f5f5);
}

.qb-footer-actions {
  display: flex;
  gap: 1rem;
}

/* 重置按钮样式 */
.btn-reset {
  padding: 0.6rem 1.2rem;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 50px;
  background: var(--white, #ffffff);
  color: var(--text-dark, #333);
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-reset:hover {
  background: var(--bg-light, #f5f5f5);
  border-color: var(--text-light, #666);
  color: var(--text-dark, #333);
}

.btn-reset i {
  font-size: 0.85rem;
}

/* 面板头部样式 */
.qb-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}

.qb-panel-header-content {
  flex: 1;
  min-width: 0;
}

.qb-panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.35rem;
}

.qb-panel-title-row h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--text-dark);
}

.qb-panel-title-row .qb-icon-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: var(--bg-light);
  color: var(--text-light);
  cursor: pointer;
  transition: all 0.2s ease;
}

.qb-panel-title-row .qb-icon-btn:hover {
  background: var(--primary-color);
  color: #fff;
}

.qb-panel-title-row .qb-icon-btn .icon {
  width: 14px;
  height: 14px;
}

.qb-panel-desc {
  margin: 0 0 0.6rem;
  font-size: 0.85rem;
  color: var(--text-light);
  line-height: 1.4;
}

/* 导航栏说明样式 */
.qb-panel-tips {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.qb-tip-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.75rem;
  color: var(--text-light);
  line-height: 1.4;
}

.qb-tip-num {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  font-size: 0.65rem;
  font-weight: 600;
  background: var(--bg-light);
  color: var(--text-light);
}

.qb-tip-item:first-child .qb-tip-num {
  background: linear-gradient(135deg, var(--primary-color, #4361ee), #6b8cff);
  color: #fff;
}

.qb-tip-item:last-child .qb-tip-num {
  background: linear-gradient(135deg, var(--success-color, #19a774), #2ecc94);
  color: #fff;
}

@media (max-width: 768px) {
  .qb-expanded-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .qb-footer-actions {
    width: 100%;
    flex-direction: column;
  }

  .qb-footer-actions .btn {
    width: 100%;
  }

  .btn-reset {
    width: 100%;
    justify-content: center;
  }
}

/* 滑条样式 */
.qb-slider-container {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.qb-slider {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--bg-light);
  outline: none;
  -webkit-appearance: none;
  cursor: pointer;
}

.qb-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--primary-color, #4361ee);
  cursor: pointer;
  transition: transform 0.2s ease;
}

.qb-slider::-webkit-slider-thumb:hover {
  transform: scale(1.15);
}

.qb-slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--primary-color, #4361ee);
  cursor: pointer;
  border: none;
}

.qb-slider-value {
  min-width: 24px;
  text-align: center;
  font-weight: 600;
  color: var(--primary-color, #4361ee);
  font-size: 1.1rem;
}
</style>
