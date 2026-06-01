import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import QuestionBankView from '../views/QuestionBankView.vue'
import HistoryView from '../views/HistoryView.vue'
import LoginView from '../views/LoginView.vue'
import MyQuestionsView from '../views/MyQuestionsView.vue'
import ProblemDetailView from '../views/ProblemDetailView.vue'
import TutorialView from '../views/TutorialView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/question-bank',
      name: 'question-bank',
      component: QuestionBankView,
    },
    {
      path: '/my-questions',
      name: 'my-questions',
      component: MyQuestionsView,
    },
    {
      path: '/history',
      name: 'history',
      component: HistoryView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/problem/:id',
      name: 'problem-detail',
      component: ProblemDetailView,
    },
    {
      path: '/tutorial',
      name: 'tutorial',
      component: TutorialView,
    },
  ],
})

export default router
