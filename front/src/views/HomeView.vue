<script setup>
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import Navbar from '@/components/Navbar.vue'
import heroBg from '../../svg/bg.jpg'

// 教程弹窗状态
const showTutorial = ref(false)

const tutorialMarkdown = ref(`# CodeMaster 使用教程

## 快速开始（1–2 分钟）
1. 打开首页，点击"开始刷题"进入题库页面。  
2. 在左侧筛选面板选择：数据结构（如数组、链表、树等）、算法（如动态规划、贪心等）、难度（简单/中等/困难）、场景（竞赛/面试/练习）。  
3. 设置题目数量和补充要求，点击"生成题目"获取个性化题目。  
4. 点击题目卡片进入详情页，阅读题目描述，在代码编辑器中编写代码。  
5. 点击"运行"测试自定义输入，或点击"提交"获取评测结果。  
6. 需要 AI 辅助时，点击"AI 助手"按钮打开聊天窗口。

## 题库与题目生成
- **筛选条件**：支持多选数据结构（16种）和算法（24种）标签  
- **难度级别**：简单、中等、困难  
- **使用场景**：竞赛、面试、练习  
- **生成数量**：每次可生成 1-10 道题目  
- **补充要求**：可输入特定需求，如"包含边界条件测试"  
- **我的题目**：生成记录可在"我的题目"中查看和管理

## 题目详情与代码编辑
- **代码编辑器**：基于 Monaco Editor，支持语法高亮和自动补全  
- **语言支持**：Java、C++、C、Python 3、JavaScript、Go  
- **运行测试**：支持自定义输入进行测试  
- **提交评测**：提交后查看运行时间、内存占用、通过率等指标  
- **Markdown 渲染**：题目描述、输入输出示例均支持 Markdown 格式

## AI 助手使用指南
- **启动方式**：在题目详情页点击"AI 助手"按钮，会在新窗口打开聊天界面  
- **流式回答**：AI 生成内容实时流式输出，无需等待完整响应  
- **深度思考模式**：勾选后，模型先输出思维链（reasoning），再输出最终答案  
- **中途停止**：点击"停止"按钮可中断当前回答  
- **复制与反馈**：回答内容支持一键复制和点赞/踩反馈  
- **提问技巧**：描述清晰、指定语言、给出示例输入输出、分步提问效果更好

## 学习记录与历史
- **我的题目**：查看已生成的题目列表，支持搜索和难度/主题筛选  
- **历史记录**：查看已提交过的题目，包含代码、评测结果和性能分析  
- **性能指标**：查看运行时间、内存占用、超越用户比例等数据

## 常见问题
- **答案显示为 Markdown 原文**：可复制到本地编辑器查看渲染效果  
- **流式响应慢或中断**：检查网络连接，重试或切换模式  
- **深度思考输出很长**：可折叠思维链，仅查看最终答案  
- **代码无法运行**：检查语言选择是否正确，确保代码符合语法规范

## 小技巧
- 提问越具体越好：说明输入规模、期望复杂度与语言  
- 需要运行代码：明确语言与运行环境  
- 分步提问：先要思路，再要伪代码，最后要完整实现  
- 利用场景筛选：面试场景侧重基础题，竞赛场景侧重进阶题  
- 定期复习：我的题目中可查看历史生成记录进行复习

如需返回首页，点击弹窗右上角"关闭"按钮。`)

const tutorialHtml = computed(() => marked.parse(tutorialMarkdown.value))

const openTutorial = () => {
  showTutorial.value = true
}

const closeTutorial = () => {
  showTutorial.value = false
}

const progressChartRef = ref(null)
let chartInstance

const chartOption = {
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow',
    },
  },
  legend: {
    data: ['已完成', '待完成', '正确率'],
    bottom: 0,
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '13%',
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: ['数组', '链表', '栈/队列', '递归', '树', '图', '动态规划', '排序'],
  },
  yAxis: [
    {
      type: 'value',
      name: '题目数',
      position: 'left',
    },
    {
      type: 'value',
      name: '正确率',
      position: 'right',
      max: 100,
      axisLabel: {
        formatter: '{value}%',
      },
    },
  ],
  series: [
    {
      name: '已完成',
      type: 'bar',
      data: [14, 22, 18, 26, 35, 20, 16, 12],
      color: '#4361ee',
    },
    {
      name: '待完成',
      type: 'bar',
      data: [5, 4, 7, 3, 8, 12, 10, 6],
      color: '#a0b1f5',
    },
    {
      name: '正确率',
      type: 'line',
      yAxisIndex: 1,
      data: [73, 86, 79, 85, 82, 75, 78, 81],
      color: '#19a774',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: {
        width: 3,
      },
    },
  ],
}

import { useRouter } from 'vue-router'

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  if (progressChartRef.value) {
    chartInstance = echarts.init(progressChartRef.value)
    chartInstance.setOption(chartOption)
    window.addEventListener('resize', handleResize)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
  }
})
</script>

<template>
  <div>
    <Navbar />

    <!-- 教程弹窗 -->
    <Teleport to="body">
      <div v-if="showTutorial" class="tutorial-modal-overlay" @click.self="closeTutorial">
        <div class="tutorial-modal">
          <div class="tutorial-modal-header">
            <h2>使用教程</h2>
            <button class="tutorial-close-btn" @click="closeTutorial">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div class="tutorial-modal-body">
            <div v-html="tutorialHtml"></div>
          </div>
        </div>
      </div>
    </Teleport>

    <main class="container">
      <section class="hero">
        <div class="hero-content">
          <h1 class="hero-title">
            提升你的<span class="title-highlight">算法能力</span><br />成为技术领域的佼佼者
          </h1>
          <p class="hero-subtitle">
            CodeMaster提供精心设计的算法题库，由浅入深的练习路径，以及强大的学习社区，帮助您在编程面试中脱颖而出，提升解决问题的能力。
          </p>
          <div class="hero-buttons">
            <button class="btn btn-signup">开始刷题</button>
            <button class="btn btn-login" @click="openTutorial">查看教程</button>
          </div>
        </div>
        <div
          class="hero-img"
          :style="{ backgroundImage: `url(${heroBg})` }"
          aria-hidden="true"
        />
      </section>

    <!-- 教程作为独立页面（/tutorial），此处不再展示弹窗 -->

      <section class="section">
        <div class="section-title">
          <h2>全面提升技术能力</h2>
          <p>精心设计的题库和练习路径，由浅入深，循序渐进</p>
        </div>

        <div class="categories">
          <div class="category-card">
            <div class="card-icon">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path
                  d="M10 20h4v-1h-4v-1h4v-1h-4v-1h1a2 2 0 0 0 2-2v-4a1 1 0 0 0-1-1H9a1 1 0 0 0-1 1v1a1 1 0 0 0 1 1h2v1h-1a1 1 0 0 0-1 1v1a1 1 0 0 0 1 1h3v1h-3a1 1 0 0 0-1 1v1a3 3 0 0 0 3 3zm-1-8a1 1 0 0 0 0 2h2a1 1 0 0 0 1-1v-1a1 1 0 0 0-1-1H9zm0 4a1 1 0 0 0 0 2h1a1 1 0 0 0 1-1v-1a1 1 0 0 0-1-1H9zm6-6h-2v-2h2v2zm3-6H2v16h20V4zm-2 14H4V6h16v12z"
                />
              </svg>
            </div>
            <div>
              <h3 class="card-title">数据结构训练</h3>
              <p class="card-desc">数组、链表、树、图等常用数据结构的深入练习</p>
            </div>
          </div>

          <div class="category-card">
            <div class="card-icon">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path
                  d="M17.66 4.52a10 10 0 0 1 2.28 11.28c-2.72 7-10.59 9.94-17.6 7.2A10 10 0 0 1 4.48 6.34c3.32-1.28 7.16-.85 10.3 1.16l-6.1 6.1a3 3 0 1 0 4.24 4.24l6.1-6.1c2-3.14 2.43-6.98 1.14-10.32zm-7.84 8.65l3.53-3.53a5 5 0 0 0-7.07-7.07l-3.53 3.53a5 5 0 0 0 7.07 7.07z"
                />
              </svg>
            </div>
            <div>
              <h3 class="card-title">算法专题</h3>
              <p class="card-desc">排序、搜索、动态规划等经典算法题目详解</p>
            </div>
          </div>

        </div>
      </section>



      <section class="section">
        <div class="section-title">
          <h2>学习进度可视化分析</h2>
          <p>清晰展示您的学习路线与进度，跟踪算法能力提升</p>
        </div>

        <div ref="progressChartRef" class="chart-container" role="img" aria-label="学习进度图表" />
      </section>
    </main>

<!--    <footer class="footer">-->
<!--      <div class="footer-container">-->
<!--        <div class="footer-col">-->
<!--          <h3>CodeMaster</h3>-->
<!--          <p style="color: var(&#45;&#45;text-light); margin-top: 1.5rem">-->
<!--            专业的算法学习和刷题平台，致力于帮助开发者提升编程能力和面试竞争力。-->
<!--          </p>-->
<!--        </div>-->

<!--        <div class="footer-col">-->
<!--          <h3>快速导航</h3>-->
<!--          <ul>-->
<!--            <li><a href="#">网站首页</a></li>-->
<!--            <li><a href="#">智能题库</a></li>-->
<!--            <li><a href="#">学习路线</a></li>-->
<!--            <li><a href="#">每日一练</a></li>-->
<!--            <li><a href="#">竞赛活动</a></li>-->
<!--          </ul>-->
<!--        </div>-->

<!--        <div class="footer-col">-->
<!--          <h3>资源中心</h3>-->
<!--          <ul>-->
<!--            <li><a href="#">数据结构教程</a></li>-->
<!--            <li><a href="#">算法详解</a></li>-->
<!--            <li><a href="#">面试宝典</a></li>-->
<!--            <li><a href="#">社区讨论</a></li>-->
<!--            <li><a href="#">常见问题</a></li>-->
<!--          </ul>-->
<!--        </div>-->

<!--        <div class="footer-col">-->
<!--          <h3>联系我们</h3>-->
<!--          <ul>-->
<!--            <li><a href="#">意见反馈</a></li>-->
<!--            <li><a href="#">商务合作</a></li>-->
<!--            <li><a href="#">加入我们</a></li>-->
<!--            <li><a href="#">关于我们</a></li>-->
<!--          </ul>-->
<!--        </div>-->
<!--      </div>-->

<!--      <div class="copyright">-->
<!--        <p>&copy; 2023 CodeMaster 算法学习平台. 保留所有权利</p>-->
<!--      </div>-->
<!--    </footer>-->
  </div>
</template>

<style scoped>
/* 教程弹窗样式 */
.tutorial-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  animation: fadeIn 0.2s ease;
}

.tutorial-modal {
  background: var(--white);
  border-radius: var(--radius);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  max-width: 800px;
  width: 100%;
  max-height: 85vh;
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

.tutorial-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem 2rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--primary-light);
  flex-shrink: 0;
}

.tutorial-modal-header h2 {
  font-size: 1.5rem;
  color: var(--text-dark);
  margin: 0;
}.tutorial-close-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--bg-light);
  color: var(--text-dark);
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: var(--transition);
  padding: 0;
}

.tutorial-close-btn svg {
  width: 18px;
  height: 18px;
}

.tutorial-close-btn:hover {
  background: var(--primary-color);
  color: white;
}

.tutorial-modal-body {
  overflow-y: auto;
  flex: 1;
  padding: 2rem;
}

/* 教程内容样式 */
.tutorial-modal-body :deep(h1) {
  font-size: 1.8rem;
  color: var(--text-dark);
  margin-bottom: 1.5rem;
  padding-bottom: 0.8rem;
  border-bottom: 2px solid var(--primary-color);
}

.tutorial-modal-body :deep(h2) {
  font-size: 1.4rem;
  color: var(--primary-color);
  margin: 1.8rem 0 1rem;
}

.tutorial-modal-body :deep(h3) {
  font-size: 1.1rem;
  color: var(--text-dark);
  margin: 1.4rem 0 0.8rem;
  font-weight: 600;
}

.tutorial-modal-body :deep(p) {
  color: var(--text-dark);
  line-height: 1.8;
  margin-bottom: 1rem;
}

.tutorial-modal-body :deep(ul),
.tutorial-modal-body :deep(ol) {
  padding-left: 1.5rem;
  margin-bottom: 1rem;
}

.tutorial-modal-body :deep(li) {
  color: var(--text-dark);
  line-height: 1.8;
  margin-bottom: 0.5rem;
}

.tutorial-modal-body :deep(strong) {
  color: var(--primary-color);
  font-weight: 600;
}

.tutorial-modal-body :deep(code) {
  background: var(--primary-light);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  color: var(--primary-color);
  font-size: 0.9rem;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .tutorial-modal-overlay {
    padding: 1rem;
  }

  .tutorial-modal {
    max-height: 90vh;
  }

  .tutorial-modal-header {
    padding: 1.2rem 1.5rem;
  }

  .tutorial-modal-body {
    padding: 1.5rem;
  }

  .tutorial-modal-body :deep(h1) {
    font-size: 1.5rem;
  }

  .tutorial-modal-body :deep(h2) {
    font-size: 1.2rem;
  }
}
</style>
