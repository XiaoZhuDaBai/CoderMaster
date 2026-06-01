<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Navbar from '@/components/Navbar.vue'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()

const userStore = useUserStore()
const searchQuery = ref('')
const selectedDifficulty = ref('all')
const selectedTopics = ref([])
const isLoading = ref(false)
const myQuestions = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const totalProblems = ref(0)
const availableTags = ref([])

// 防抖定时器
let searchDebounceTimer = null

// 标签展开/收起
const showAllTopics = ref(false)
const initialDisplayCount = 6

const toggleTopic = (topic) => {
  if (selectedTopics.value.includes(topic)) {
    selectedTopics.value = selectedTopics.value.filter(t => t !== topic)
  } else {
    selectedTopics.value.push(topic)
  }
}

const displayTopics = computed(() => {
  if (showAllTopics.value || topicOptions.value.length <= initialDisplayCount) {
    return topicOptions.value
  }
  return topicOptions.value.slice(0, initialDisplayCount)
})

// 主题选项从后端返回的 availableTags 获取
const topicOptions = computed(() => {
  return availableTags.value.map(t => ({ label: t, value: t }))
})

// 详情模态框相关
const showDetailModal = ref(false)
const selectedQuestion = ref(null)

const difficultyMap = {
  0: 'easy',
  1: 'medium',
  2: 'hard',
}

const difficultyReverseMap = {
  easy: 0,
  medium: 1,
  hard: 2,
}

const transformProblem = (problem) => {
  const topic = problem.tagNames && problem.tagNames.length > 0 
    ? problem.tagNames[0] 
    : (problem.tagIds && problem.tagIds.length > 0 ? String(problem.tagIds[0]) : '其他')
  
  let difficulty = 'medium'
  if (typeof problem.difficulty === 'number') {
    difficulty = difficultyMap[problem.difficulty] || 'medium'
  } else if (typeof problem.difficulty === 'string') {
    const strMap = { '简单': 'easy', '中等': 'medium', '困难': 'hard' }
    difficulty = strMap[problem.difficulty] || 'medium'
  }
  
  // 将 generatedAt 转换为日期字符串
  let createdAt = ''
  if (problem.generatedAt) {
    createdAt = new Date(problem.generatedAt).toISOString().split('T')[0]
  } else {
    createdAt = new Date().toISOString().split('T')[0]
  }
  
  const isNew = problem.isNew ?? false
  
  return {
    id: problem.contentHash || problem.id || Math.random().toString(36).substr(2, 9),
    title: problem.title || '未命名题目',
    topic: topic,
    difficulty: difficulty,
    scenario: problem.source || '练习',
    description: problem.description || problem.content || '暂无描述',
    tags: problem.tagNames || [],
    createdAt: createdAt,
    status: 'pending',
    isNew: isNew,
    contentHash: problem.contentHash,
    timeLimit: problem.timeLimit,
    memoryLimit: problem.memoryLimit,
    _original: problem,
    inputDesc: problem.inputDesc,
    outputDesc: problem.outputDesc,
    examples: problem.examples,
    questionId: problem.questionId || problem.id,
  }
}

// 加载题目列表（分页）
const loadQuestions = async () => {
  if (!userStore.isLoggedIn) {
    myQuestions.value = []
    return
  }

  const userKey = String(userStore.userId || userStore.userInfo?.userId || '')
  if (!userKey) {
    console.warn('无法获取用户ID')
    return
  }

  isLoading.value = true

  try {
    const result = await api.getProblemsPaged(userKey, {
      searchKeyword: searchQuery.value || null,
      difficulty: selectedDifficulty.value === 'all' ? null : difficultyReverseMap[selectedDifficulty.value],
      tagNames: selectedTopics.value.length > 0 ? selectedTopics.value : null,
      page: currentPage.value,
      pageSize: pageSize.value,
    })

    if (result && result.status && result.data) {
      const data = result.data
      
      // 更新可用标签
      availableTags.value = data.availableTags || []
      
      // 更新总数
      totalProblems.value = data.total || 0
      
      // 转换数据
      if (data.problems && data.problems.length > 0) {
        myQuestions.value = data.problems.map(p => {
          try {
            return transformProblem(p)
          } catch (e) {
            console.error('转换题目数据失败:', p, e)
            return null
          }
        }).filter(Boolean)
      } else {
        myQuestions.value = []
      }
    } else {
      console.warn('API返回格式不正确:', result)
      myQuestions.value = []
      totalProblems.value = 0
    }
  } catch (error) {
    console.error('加载题目列表失败:', error)
    myQuestions.value = []
    totalProblems.value = 0
  } finally {
    isLoading.value = false
  }
}

// 搜索防抖（300ms）
watch(searchQuery, () => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
  }
  searchDebounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadQuestions()
  }, 300)
})

// 难度变化时重新加载
watch(selectedDifficulty, () => {
  currentPage.value = 1
  loadQuestions()
})

// 标签变化时重新加载
watch(selectedTopics, () => {
  currentPage.value = 1
  loadQuestions()
}, { deep: true })

// 监听 currentPage 变化
watch(currentPage, () => {
  loadQuestions()
})

const difficultyOptions = [
  { label: '全部', value: 'all' },
  { label: '简单', value: 'easy' },
  { label: '中等', value: 'medium' },
  { label: '困难', value: 'hard' },
]

const getDifficultyClass = (difficulty) => {
  return {
    easy: 'tag-basic',
    medium: 'tag-intermediate',
    hard: 'tag-advanced',
  }[difficulty] || 'tag-basic'
}

const getDifficultyLabel = (difficulty) => {
  return {
    easy: '简单',
    medium: '中等',
    hard: '困难',
  }[difficulty] || difficulty
}

const getStatusClass = (status) => {
  return {
    completed: 'status-success',
    'in-progress': 'status-partial',
    pending: 'status-failed',
  }[status] || ''
}

const getStatusLabel = (status) => {
  return {
    completed: '已完成',
    'in-progress': '进行中',
    pending: '待开始',
  }[status] || status
}

const viewDetail = (question) => {
  selectedQuestion.value = question
  showDetailModal.value = true
}

const closeDetailModal = () => {
  showDetailModal.value = false
  selectedQuestion.value = null
}

const startPractice = (question) => {
  const originalProblem = question._original || question
  
  const problemData = {
    title: originalProblem.title || question.title,
    description: originalProblem.description || originalProblem.content || question.description,
    content: originalProblem.content || originalProblem.description || question.description,
    difficulty: typeof originalProblem.difficulty === 'number' 
      ? originalProblem.difficulty 
      : (question.difficulty === 'easy' ? 0 : question.difficulty === 'medium' ? 1 : 2),
    tagNames: originalProblem.tagNames || question.tags || [],
    contentHash: originalProblem.contentHash || question.contentHash || question.id,
    questionId: originalProblem.questionId || originalProblem.id || question.id,
    timeLimit: originalProblem.timeLimit || question.timeLimit,
    memoryLimit: originalProblem.memoryLimit || question.memoryLimit,
    source: originalProblem.source || question.scenario,
    createdAt: originalProblem.createdAt || originalProblem.createTime || originalProblem.generatedAt || question.createdAt,
    inputDesc: originalProblem.inputDesc,
    outputDesc: originalProblem.outputDesc,
    examples: originalProblem.examples,
    tagIds: originalProblem.tagIds,
    generatedAt: originalProblem.generatedAt,
    createTime: originalProblem.createTime,
  }
  
  sessionStorage.setItem('currentProblem', JSON.stringify(problemData))
  
  router.push({
    name: 'problem-detail',
    params: { id: question.contentHash || question.id || 'unknown' },
  })
}

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(totalProblems.value / pageSize.value)
})

// 分页变化
const handlePageChange = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
  }
}

onMounted(() => {
  loadQuestions()
})
</script>

<template>
  <div>
    <Navbar />
    <div class="my-questions-container">
      <div class="my-questions-filters">
        <div class="search-box">
          <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
            <path d="M469.333333 128C270.933333 128 106.666667 292.266667 106.666667 490.666667c0 198.4 164.266667 362.666667 362.666666 362.666666 198.4 0 362.666667-164.266667 362.666667-362.666666 0-198.4-164.266667-362.666667-362.666667-362.666667z m0 640c-153.6 0-277.333333-123.733333-277.333333-277.333333 0-153.6 123.733333-277.333333 277.333333-277.333334 153.6 0 277.333333 123.733333 277.333334 277.333334 0 153.6-123.733333 277.333333-277.333334 277.333333zM853.333333 853.333333l-128-128c-25.6-25.6-66.133333-25.6-91.733333 0s-25.6 66.133333 0 91.733334l128 128c12.8 12.8 29.866667 19.2 46.933333 19.2s34.133333-6.4 46.933334-19.2c25.6-25.6 25.6-66.133333 0-91.733334z" fill="currentColor"/>
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            class="search-input"
            placeholder="搜索题目、标签或描述..."
          />
        </div>

        <div class="filter-group">
          <label>难度：</label>
          <select v-model="selectedDifficulty" class="filter-select">
            <option v-for="option in difficultyOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div v-if="topicOptions.length > 0" class="filter-group topic-filter-group">
          <label class="filter-label">主题：</label>
          <div class="topic-tags">
            <button
              v-for="topic in displayTopics"
              :key="topic.value"
              type="button"
              class="topic-tag-btn"
              :class="{ active: selectedTopics.includes(topic.value) }"
              @click="toggleTopic(topic.value)"
            >
              {{ topic.label }}
            </button>
            <button
              v-if="topicOptions.length > initialDisplayCount"
              type="button"
              class="expand-topics-btn"
              @click="showAllTopics = !showAllTopics"
            >
              <template v-if="showAllTopics">
                收起 <i class="fas fa-chevron-up"></i>
              </template>
              <template v-else>
                +{{ topicOptions.length - initialDisplayCount }} 更多
              </template>
            </button>
          </div>
          <button
            v-if="selectedTopics.length > 0"
            type="button"
            class="clear-topics-btn"
            @click="selectedTopics = []"
          >
            清除
          </button>
        </div>

        <div class="questions-stats">
          共 {{ totalProblems }} 道题目
        </div>
      </div>

      <div v-if="myQuestions.length === 0 && !isLoading" class="my-questions-empty">
        <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon empty-icon">
          <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" fill="currentColor"/>
          <path d="M464 336a48 48 0 1 0 96 0 48 48 0 1 0-96 0zM512 752c-22.1 0-40-17.9-40-40 0-13 6.7-24.6 16.8-31.2l50.4-29.1c5-2.9 8.8-7.4 11.2-12.5 2.4-5.1 3.6-10.6 3.6-16.2v-64c0-17.7-14.3-32-32-32s-32 14.3-32 32v48h-64v-48c0-52.9 43.1-96 96-96s96 43.1 96 96v64c0 17.7-14.3 32-32 32s-32-14.3-32-32v-48h-64v48c0 13-6.7 24.6-16.8 31.2l-50.4 29.1c-5 2.9-8.8 7.4-11.2 12.5-2.4 5.1-3.6 10.6-3.6 16.2 0 22.1-17.9 40-40 40z" fill="currentColor"/>
        </svg>
        <h2>暂无题目</h2>
        <p>没有找到匹配的题目，请尝试调整筛选条件</p>
      </div>

      <div v-else class="my-questions-grid">
        <article
          v-for="question in myQuestions"
          :key="question.id"
          class="my-question-card"
        >
          <header class="question-card-header">
            <h3>
              {{ question.title }}
              <span v-if="question.isNew" class="new-badge">新</span>
            </h3>
            <div class="question-card-badges">
              <span class="tag" :class="getDifficultyClass(question.difficulty)">
                {{ getDifficultyLabel(question.difficulty) }}
              </span>
              <span class="status-badge" :class="getStatusClass(question.status)">
                {{ getStatusLabel(question.status) }}
              </span>
            </div>
          </header>

          <div class="question-card-body">
            <p class="question-description">{{ question.description }}</p>
            
            <div class="question-meta">
              <div class="meta-item">
                <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                  <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" fill="currentColor"/>
                  <path d="M686.7 638.6L544.1 535.5V288c0-17.7-14.3-32-32-32s-32 14.3-32 32v275.4c0 8.6 3.9 16.7 10.5 22.1l176.7 149.2c5.2 4.4 12.5 6.6 19.8 6.6 9.8 0 19.5-4.1 26.4-12.1 9.8-10.9 11.2-27.1 3.2-39.6z" fill="currentColor"/>
                </svg>
                <span>{{ question.createdAt }}</span>
              </div>
              <div class="meta-item">
                <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                  <path d="M880 112H144c-17.7 0-32 14.3-32 32v736c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V144c0-17.7-14.3-32-32-32z m-40 728H184V184h656v656z" fill="currentColor"/>
                  <path d="M304 304h416v64H304zM304 464h416v64H304zM304 624h256v64H304z" fill="currentColor"/>
                </svg>
                <span>{{ question.topic }}</span>
              </div>
              <div class="meta-item">
                <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
                  <path d="M880 112H144c-17.7 0-32 14.3-32 32v736c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V144c0-17.7-14.3-32-32-32z m-40 728H184V184h656v656z" fill="currentColor"/>
                </svg>
                <span>{{ question.scenario }}</span>
              </div>
            </div>

            <div class="question-tags">
              <span
                v-for="tag in question.tags"
                :key="tag"
                class="question-tag"
              >
                {{ tag }}
              </span>
            </div>
          </div>

          <footer class="question-card-footer">
            <button type="button" class="btn btn-primary" @click="startPractice(question)">开始练习</button>
            <button type="button" class="btn btn-secondary" @click="viewDetail(question)">查看详情</button>
          </footer>
        </article>
      </div>

      <!-- 分页控件 -->
      <div v-if="totalPages > 1" class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage === 1"
          @click="handlePageChange(currentPage - 1)"
        >
          上一页
        </button>
        <span class="page-info">
          第 {{ currentPage }} / {{ totalPages }} 页
        </span>
        <button
          class="page-btn"
          :disabled="currentPage === totalPages"
          @click="handlePageChange(currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <!-- 详情模态框 -->
    <div v-if="showDetailModal && selectedQuestion" class="detail-modal" @click.self="closeDetailModal">
      <div class="detail-modal-content">
        <div class="detail-modal-header">
          <h2>{{ selectedQuestion.title }}</h2>
          <button class="close-btn" @click="closeDetailModal">
            <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" class="icon">
              <path d="M563.8 512l262.5-262.5c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L518.5 466.7 256 204.3c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3L473.2 512 210.7 774.5c-12.5 12.5-12.5 32.8 0 45.3 6.2 6.2 14.4 9.4 22.6 9.4s16.4-3.1 22.6-9.4L518.5 557.3l262.5 262.5c6.2 6.2 14.4 9.4 22.6 9.4s16.4-3.1 22.6-9.4c12.5-12.5 12.5-32.8 0-45.3L563.8 512z" fill="currentColor"/>
            </svg>
          </button>
        </div>
        
        <div class="detail-modal-body">
          <div class="detail-section">
            <div class="detail-badges">
              <span class="tag" :class="getDifficultyClass(selectedQuestion.difficulty)">
                {{ getDifficultyLabel(selectedQuestion.difficulty) }}
              </span>
              <span class="status-badge" :class="getStatusClass(selectedQuestion.status)">
                {{ getStatusLabel(selectedQuestion.status) }}
              </span>
              <span v-if="selectedQuestion.isNew" class="new-badge">新</span>
            </div>
          </div>

          <div class="detail-section">
            <h3>题目描述</h3>
            <p class="detail-description">{{ selectedQuestion.description }}</p>
          </div>

          <div class="detail-section">
            <h3>题目信息</h3>
            <div class="detail-info-grid">
              <div class="detail-info-item">
                <span class="info-label">主题：</span>
                <span class="info-value">{{ selectedQuestion.topic }}</span>
              </div>
              <div class="detail-info-item">
                <span class="info-label">场景：</span>
                <span class="info-value">{{ selectedQuestion.scenario }}</span>
              </div>
              <div class="detail-info-item">
                <span class="info-label">创建时间：</span>
                <span class="info-value">{{ selectedQuestion.createdAt }}</span>
              </div>
              <div v-if="selectedQuestion.timeLimit" class="detail-info-item">
                <span class="info-label">时间限制：</span>
                <span class="info-value">{{ selectedQuestion.timeLimit }}ms</span>
              </div>
              <div v-if="selectedQuestion.memoryLimit" class="detail-info-item">
                <span class="info-label">内存限制：</span>
                <span class="info-value">{{ selectedQuestion.memoryLimit }}MB</span>
              </div>
            </div>
          </div>

          <div v-if="selectedQuestion.tags && selectedQuestion.tags.length > 0" class="detail-section">
            <h3>标签</h3>
            <div class="detail-tags">
              <span
                v-for="tag in selectedQuestion.tags"
                :key="tag"
                class="detail-tag"
              >
                {{ tag }}
              </span>
            </div>
          </div>
        </div>

        <div class="detail-modal-footer">
          <button type="button" class="btn btn-secondary" @click="closeDetailModal">关闭</button>
          <button type="button" class="btn btn-primary" @click="startPractice(selectedQuestion)">开始练习</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 加载状态 */
.my-questions-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: var(--text-light);
}

.my-questions-loading i {
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

.my-questions-loading p {
  font-size: 1rem;
  color: var(--text-dark);
}

/* 新题目标记 */
.new-badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  margin-left: 0.5rem;
  background: linear-gradient(135deg, #ff6b6b, #ee5a6f);
  color: white;
  font-size: 0.7rem;
  font-weight: 600;
  border-radius: 999px;
  vertical-align: middle;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

.question-card-header h3 {
  display: flex;
  align-items: center;
  flex: 1;
  margin: 0;
}

/* 详情模态框样式 */
.detail-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 2rem;
}

.detail-modal-content {
  background: var(--white, #ffffff);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-modal-header {
  padding: 1.5rem 2rem;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.detail-modal-header h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  flex: 1;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-light, #666);
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-light, #f5f5f5);
  color: var(--text-dark, #333);
}

.close-btn .icon {
  width: 20px;
  height: 20px;
}

.detail-modal-body {
  padding: 2rem;
  overflow-y: auto;
  flex: 1;
}

.detail-section {
  margin-bottom: 2rem;
}

.detail-section:last-child {
  margin-bottom: 0;
}

.detail-section h3 {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-dark, #333);
  margin-bottom: 1rem;
}

.detail-badges {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  align-items: center;
}

.detail-description {
  color: var(--text-dark, #333);
  line-height: 1.8;
  white-space: pre-wrap;
  margin: 0;
}

.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.detail-info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.info-label {
  font-weight: 500;
  color: var(--text-light, #666);
  font-size: 0.9rem;
}

.info-value {
  color: var(--text-dark, #333);
  font-size: 0.9rem;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.detail-tag {
  padding: 0.4rem 0.8rem;
  background: var(--primary-light, #e8f0fe);
  color: var(--primary-color, #4361ee);
  border-radius: 999px;
  font-size: 0.85rem;
}

.detail-modal-footer {
  padding: 1.5rem 2rem;
  border-top: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .detail-modal {
    padding: 1rem;
  }

  .detail-modal-content {
    max-height: 95vh;
  }

  .detail-modal-header,
  .detail-modal-body,
  .detail-modal-footer {
    padding: 1rem 1.5rem;
  }

  .detail-info-grid {
    grid-template-columns: 1fr;
  }
}

/* 分页控件样式 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 2rem;
  margin-top: 1rem;
}

.page-btn {
  padding: 0.5rem 1rem;
  border: 1px solid var(--border-color, #e0e0e0);
  background: var(--white, #ffffff);
  color: var(--text-dark, #333);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-light, #e8f0fe);
  border-color: var(--primary-color, #4361ee);
  color: var(--primary-color, #4361ee);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: var(--text-light, #666);
  font-size: 0.9rem;
}
</style>
