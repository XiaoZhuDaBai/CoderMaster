<script setup>
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import Navbar from '@/components/Navbar.vue'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const searchQuery = ref('')
const selectedRecord = ref(null)
const showModal = ref(false)
const highlightedCode = ref('')
const isLoading = ref(false)

// 分页相关
const currentPage = ref(1)
const pageSize = ref(6)
const total = ref(0)
const historyRecords = ref([])

// 筛选相关
const selectedDifficulty = ref('all')
const selectedLanguage = ref('all')
const selectedStatus = ref('all')

const difficultyOptions = [
  { label: '全部难度', value: 'all' },
  { label: '简单', value: 0 },
  { label: '中等', value: 1 },
  { label: '困难', value: 2 },
]

const languageOptions = [
  { label: '全部语言', value: 'all' },
  { label: 'JavaScript', value: 'JavaScript' },
  { label: 'Python', value: 'Python' },
  { label: 'Java', value: 'Java' },
  { label: 'C++', value: 'C++' },
  { label: 'C', value: 'C' },
  { label: 'Go', value: 'Go' },
  { label: 'Rust', value: 'Rust' },
]

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '通过', value: 'AC' },
  { label: '部分通过', value: 'PARTIAL' },
  { label: '失败', value: 'WA' },
]

// 难度数字转文字
const difficultyMap = {
  0: '简单',
  1: '中等',
  2: '困难',
}

// 难度数字转样式类
const getDifficultyClass = (difficulty) => {
  return {
    0: 'tag-basic',
    1: 'tag-intermediate',
    2: 'tag-advanced',
  }[difficulty] || 'tag-basic'
}

// 判题状态转文字和样式
const getStatusInfo = (judgeStatus) => {
  const statusMap = {
    'AC': { text: '通过', class: 'status-success' },
    'WA': { text: '失败', class: 'status-failed' },
    'TLE': { text: '失败', class: 'status-failed' },
    'MLE': { text: '失败', class: 'status-failed' },
    'RE': { text: '失败', class: 'status-failed' },
    'CE': { text: '失败', class: 'status-failed' },
    'PARTIAL': { text: '部分通过', class: 'status-partial' },
    'PENDING': { text: '待判题', class: 'status-pending' },
    'JUDGING': { text: '判题中', class: 'status-pending' },
  }
  return statusMap[judgeStatus] || { text: judgeStatus, class: '' }
}

// 加载提交记录
const loadSubmissions = async () => {
  if (!userStore.userId) {
    historyRecords.value = []
    total.value = 0
    return
  }

  isLoading.value = true
  try {
    const params = {
      userId: userStore.userId,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      questionTitle: searchQuery.value.trim() || undefined,
      language: selectedLanguage.value !== 'all' ? selectedLanguage.value : undefined,
      judgeStatus: selectedStatus.value !== 'all' ? selectedStatus.value : undefined,
    }
    
    const result = await api.getSubmissionHistory(params)
    if (result.status && result.data) {
      historyRecords.value = result.data.records || []
      total.value = result.data.total || 0
    }
  } catch (error) {
    console.error('加载提交记录失败:', error)
    historyRecords.value = []
    total.value = 0
  } finally {
    isLoading.value = false
  }
}

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

// 筛选后的记录（前端额外按难度筛选）
const filteredRecords = computed(() => {
  return historyRecords.value.filter((record) => {
    // 难度筛选
    const matchesDifficulty = selectedDifficulty.value === 'all' || 
      record.difficulty === selectedDifficulty.value
    
    return matchesDifficulty
  })
})

// 监听筛选条件变化，重置页码并重新加载
watch([searchQuery, selectedDifficulty, selectedLanguage, selectedStatus], () => {
  currentPage.value = 1
  loadSubmissions()
})

// 翻页
const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadSubmissions()
}

// 语言映射到 highlight.js 的语言标识
const getLanguageAlias = (language) => {
  const langMap = {
    JavaScript: 'javascript',
    Python: 'python',
    Java: 'java',
    'C++': 'cpp',
    C: 'c',
    'C#': 'csharp',
    Go: 'go',
    Rust: 'rust',
    TypeScript: 'typescript',
  }
  return langMap[language] || 'plaintext'
}

// 高亮代码
const highlightCode = async (code, language) => {
  if (!code) {
    highlightedCode.value = '<span class="text-muted">代码不可用</span>'
    return
  }
  await nextTick()
  try {
    const lang = getLanguageAlias(language)
    const highlighted = hljs.highlight(code, { language: lang }).value
    highlightedCode.value = highlighted
  } catch (err) {
    highlightedCode.value = code
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;')
  }
}

// 查看详情
const selectRecord = async (record) => {
  selectedRecord.value = record
  showModal.value = true
  document.body.style.overflow = 'hidden'
  if (record.code) {
    await highlightCode(record.code, record.language)
  } else {
    highlightedCode.value = '<span class="text-muted">代码详情暂不可用</span>'
  }
}

// 监听选中记录变化，更新高亮
watch(
  () => selectedRecord.value,
  async (newRecord) => {
    if (newRecord && newRecord.code) {
      await highlightCode(newRecord.code, newRecord.language)
    }
  }
)

const closeDetail = () => {
  showModal.value = false
  selectedRecord.value = null
  document.body.style.overflow = ''
}

const handleEscape = (e) => {
  if (e.key === 'Escape' && showModal.value) {
    closeDetail()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleEscape)
  loadSubmissions()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEscape)
  document.body.style.overflow = ''
})

const copyCode = async (event) => {
  if (!selectedRecord.value?.code) return
  try {
    await navigator.clipboard.writeText(selectedRecord.value.code)
    const btn = event?.target?.closest('.copy-btn')
    if (btn) {
      const originalText = btn.innerHTML
      btn.innerHTML = '<i class="fas fa-check"></i> 已复制'
      btn.style.background = 'var(--secondary-color)'
      setTimeout(() => {
        btn.innerHTML = originalText
        btn.style.background = ''
      }, 2000)
    }
  } catch (err) {
    console.error('复制失败:', err)
  }
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 格式化运行时间
const formatRuntime = (ms) => {
  if (!ms) return 'N/A'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

// 格式化内存
const formatMemory = (kb) => {
  if (!kb) return 'N/A'
  if (kb < 1024) return `${kb}KB`
  return `${(kb / 1024).toFixed(1)}MB`
}

// 格式化示例
const formatExamples = (examplesJson) => {
  if (!examplesJson) return ''
  try {
    const examples = JSON.parse(examplesJson)
    return examples.map((ex, i) => {
      let str = `示例 ${i + 1}:\n输入:\n${ex.input}\n输出:\n${ex.output}`
      if (ex.explanation) {
        str += `\n解释:\n${ex.explanation}`
      }
      return str
    }).join('\n\n')
  } catch {
    return examplesJson
  }
}
</script>

<template>
  <div>
    <Navbar />

    <div class="history-container">
      <div class="history-search-bar">
        <div class="search-box">
          <i class="fas fa-search"></i>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索题目..."
            class="search-input"
          />
        </div>

        <div class="filter-group">
          <select v-model="selectedDifficulty" class="filter-select">
            <option v-for="option in difficultyOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="filter-group">
          <select v-model="selectedLanguage" class="filter-select">
            <option v-for="option in languageOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="filter-group">
          <select v-model="selectedStatus" class="filter-select">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="history-stats">
          <span>共 {{ total }} 条记录</span>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoading" class="history-loading">
        <i class="fas fa-spinner fa-spin"></i>
        <p>加载中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredRecords.length === 0" class="history-empty-state">
        <i class="fas fa-search"></i>
        <h2>没有找到匹配的记录</h2>
        <p>尝试调整搜索条件</p>
      </div>

      <!-- 记录列表 -->
      <div v-else class="history-cards-grid">
        <article
          v-for="record in filteredRecords"
          :key="record.submissionId"
          class="history-card"
          @click="selectRecord(record)"
        >
          <div class="card-header">
            <h3>{{ record.title || '题目 ' + record.questionId }}</h3>
            <span class="tag" :class="getDifficultyClass(record.difficulty)">
              {{ difficultyMap[record.difficulty] || '未知' }}
            </span>
          </div>

          <div class="card-body">
            <div class="card-meta">
              <span class="meta-item">
                <i class="fas fa-code"></i>
                {{ record.language }}
              </span>
              <span class="meta-item">
                <i class="fas fa-clock"></i>
                {{ formatTime(record.createTime).split(' ')[0] }}
              </span>
            </div>

            <div class="card-status">
              <span class="status-badge" :class="getStatusInfo(record.judgeStatus).class">
                <i class="fas" :class="record.judgeStatus === 'AC' ? 'fa-check' : 'fa-exclamation-triangle'"></i>
                {{ getStatusInfo(record.judgeStatus).text }}
              </span>
            </div>

            <div class="card-performance">
              <div class="perf-item">
                <span class="perf-label">运行时间</span>
                <span class="perf-value">{{ formatRuntime(record.timeCost) }}</span>
              </div>
              <div class="perf-item">
                <span class="perf-label">内存</span>
                <span class="perf-value">{{ formatMemory(record.memoryCost) }}</span>
              </div>
              <div class="perf-item">
                <span class="perf-label">通过率</span>
                <span class="perf-value">
                  {{ record.totalCases ? Math.round((record.passedCases / record.totalCases) * 100) : 0 }}%
                </span>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <button class="card-view-btn" type="button">
              <i class="fas fa-eye"></i>
              查看详情
            </button>
          </div>
        </article>
      </div>

      <!-- 分页组件 -->
      <div v-if="totalPages > 1" class="pagination">
        <button 
          class="page-btn" 
          :disabled="currentPage === 1"
          @click="goToPage(currentPage - 1)"
        >
          <i class="fas fa-chevron-left"></i>
        </button>
        
        <span class="page-info">
          第 {{ currentPage }} / {{ totalPages }} 页
        </span>
        
        <button 
          class="page-btn" 
          :disabled="currentPage === totalPages"
          @click="goToPage(currentPage + 1)"
        >
          <i class="fas fa-chevron-right"></i>
        </button>
      </div>

      <!-- 详情模态框 -->
      <div v-if="showModal && selectedRecord" class="history-modal" @click.self="closeDetail">
        <div class="modal-content">
          <div class="modal-header">
            <h2>{{ selectedRecord.title || '题目 ' + selectedRecord.questionId }}</h2>
            <button class="modal-close-btn" type="button" @click="closeDetail">
              <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                <path d="M983.722351 796.075891L701.304951 511.763073l284.312819-284.312818c51.176307-51.176307 51.176307-136.470153 0-189.541879-51.176307-51.176307-136.470153-51.176307-189.541879 0L511.763073 322.221194 227.450255 37.908376C176.273947-13.267932 90.980102-13.267932 37.908376 37.908376-13.267932 89.084683-13.267932 174.378528 37.908376 227.450255l284.312818 284.312818L37.908376 796.075891c-51.176307 51.176307-51.176307 136.470153 0 189.541879 51.176307 51.176307 136.470153 51.176307 189.541879 0l284.312818-284.312819 284.312818 284.312819c51.176307 51.176307 136.470153 51.176307 189.541879 0 51.176307-53.071726 51.176307-138.365571-1.895419-189.541879z" fill="currentColor"/>
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <div class="detail-content">
              <!-- 题目详情区域 -->
              <div class="detail-left">
                <div v-if="selectedRecord.description" class="problem-detail-section">
                  <h3>题目描述</h3>
                  <div class="problem-description" v-html="selectedRecord.description.replace(/\n/g, '<br>')"></div>

                  <div v-if="selectedRecord.inputDesc" class="problem-section">
                    <h4>输入描述</h4>
                    <div v-html="selectedRecord.inputDesc.replace(/\n/g, '<br>')"></div>
                  </div>

                  <div v-if="selectedRecord.outputDesc" class="problem-section">
                    <h4>输出描述</h4>
                    <div v-html="selectedRecord.outputDesc.replace(/\n/g, '<br>')"></div>
                  </div>

                  <div v-if="selectedRecord.examples && selectedRecord.examples !== '[]'" class="problem-section">
                    <h4>示例</h4>
                    <pre class="example-block">{{ formatExamples(selectedRecord.examples) }}</pre>
                  </div>

                  <div v-if="selectedRecord.timeLimit || selectedRecord.memoryLimit" class="problem-section">
                    <h4>限制</h4>
                    <div class="limit-info">
                      <span v-if="selectedRecord.timeLimit">时间限制: {{ selectedRecord.timeLimit }}ms</span>
                      <span v-if="selectedRecord.memoryLimit">内存限制: {{ selectedRecord.memoryLimit }}MB</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 代码区域 -->
              <div class="detail-right">
                <div class="code-header">
                  <span class="code-lang">
                    <i class="fas fa-code"></i>
                    {{ selectedRecord.language }}
                  </span>
                  <button class="copy-btn" type="button" @click="copyCode($event)">
                    <i class="fas fa-copy"></i>
                    复制代码
                  </button>
                </div>
                <pre class="code-block"><code v-html="highlightedCode"></code></pre>
              </div>
            </div>

            <div class="evaluation-section">
              <h3>测评信息</h3>
              <div class="evaluation-grid">
                <div class="evaluation-item">
                  <div class="eval-label">运行时间</div>
                  <div class="eval-value">{{ formatRuntime(selectedRecord.timeCost) }}</div>
                </div>
                <div class="evaluation-item">
                  <div class="eval-label">内存消耗</div>
                  <div class="eval-value">{{ formatMemory(selectedRecord.memoryCost) }}</div>
                </div>
                <div class="evaluation-item">
                  <div class="eval-label">测试用例</div>
                  <div class="eval-value">
                    {{ selectedRecord.passedCases || 0 }} /
                    {{ selectedRecord.totalCases || 0 }}
                  </div>
                  <div class="eval-meta">通过率
                    {{ selectedRecord.totalCases ? Math.round((selectedRecord.passedCases / selectedRecord.totalCases) * 100) : 0 }}%
                  </div>
                </div>
                <div class="evaluation-item">
                  <div class="eval-label">判题状态</div>
                  <div class="eval-value">
                    <span class="status-badge" :class="getStatusInfo(selectedRecord.judgeStatus).class">
                      {{ getStatusInfo(selectedRecord.judgeStatus).text }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 加载状态 */
.history-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: var(--text-light);
}

.history-loading i {
  font-size: 2.5rem;
  color: var(--primary-color);
  margin-bottom: 1rem;
}

.history-loading p {
  font-size: 1rem;
  color: var(--text-dark);
}

/* 分页组件 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
  padding-bottom: 2rem;
}

.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--white);
  color: var(--text-dark);
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.9rem;
  color: var(--text-light);
}

/* 文本提示 */
.text-muted {
  color: var(--text-light);
  font-style: italic;
}

/* 题目详情样式 */
.detail-left {
  padding: 1.5rem;
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
  max-height: calc(90vh - 150px);
}

.problem-detail-section {
  background: transparent;
  border-radius: 0;
  padding: 0;
}

.problem-detail-section h3 {
  font-size: 1.2rem;
  color: var(--text-dark);
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid var(--primary-light);
}

.problem-detail-section h4 {
  font-size: 1rem;
  color: var(--primary-color);
  margin: 1rem 0 0.5rem;
}

.problem-description {
  line-height: 1.8;
  color: var(--text-dark);
  white-space: pre-wrap;
}

.problem-section {
  margin-top: 1rem;
}

.problem-section div {
  line-height: 1.6;
  color: var(--text-dark);
  white-space: pre-wrap;
}

.example-block {
  background: var(--bg-light);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 1rem;
  font-size: 0.9rem;
  white-space: pre-wrap;
  overflow-x: auto;
}

.limit-info {
  display: flex;
  gap: 1.5rem;
  color: var(--text-light);
  font-size: 0.9rem;
}

/* 代码区域自适应 */
.detail-right {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.detail-right .code-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.detail-right .code-block {
  flex: 1;
  overflow-y: auto;
  max-height: none;
  margin: 0;
  padding: 1.5rem;
}
</style>
