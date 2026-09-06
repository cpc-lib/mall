<template>
  <div>
    <h2 class="adm-page-title">用户列表</h2>
    <div class="adm-card">
    <div style="margin-bottom:16px">
      <el-input v-model="keyword" placeholder="用户名关键字" clearable size="small" style="width:220px;margin-right:8px" @keyup.enter.native="search" @clear="search"/>
      <el-button size="small" type="primary" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>
    <el-table :data="records" v-loading="loading" size="small" style="width:100%">
      <el-table-column prop="id" label="ID" width="70"/>
      <el-table-column label="用户名" width="160"><template slot-scope="s"><a @click="openDetail(s.row)" style="cursor:pointer;color:#409eff">{{s.row.username}}</a></template></el-table-column>
      <el-table-column label="角色" width="120"><template slot-scope="s"><el-tag :type="s.row.role==='ROLE_ADMIN'?'danger':'primary'" size="mini">{{s.row.role}}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag :type="s.row.userStatus==='ENABLED'?'success':'danger'" size="mini">{{s.row.userStatus==='ENABLED'?'正常':'已禁用'}}</el-tag></template></el-table-column>
      <el-table-column label="注册时间" width="180"><template slot-scope="s">{{s.row.createTime?new Date(s.row.createTime).toLocaleString():'-'}}</template></el-table-column>
      <el-table-column label="操作" width="220">
        <template slot-scope="s">
          <template v-if="s.row.role!=='ROLE_ADMIN'">
            <el-button size="mini" @click="openDetail(s.row)">详情/重置密码</el-button>
            <el-button v-if="s.row.userStatus==='ENABLED'" size="mini" type="danger" @click="toggleStatus(s.row)">禁用</el-button>
            <el-button v-else size="mini" type="primary" @click="toggleStatus(s.row)">启用</el-button>
          </template>
          <span v-else style="color:#999">-</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right" background layout="total, prev, pager, next"
      :current-page.sync="page" :page-size="10" :total="total" @current-change="load"/>
    </div>
    <el-drawer :title="detail.id ? '用户详情 - ' + detail.username : '用户详情'" :visible.sync="detailVisible" size="560px" direction="rtl">
      <div v-loading="detailLoading" style="padding:0 8px">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">{{detail.id}}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{detail.username}}</el-descriptions-item>
          <el-descriptions-item label="角色"><el-tag :type="detail.role==='ROLE_ADMIN'?'danger':'primary'" size="small">{{detail.role}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detail.userStatus==='ENABLED'?'success':'danger'" size="small">{{detail.userStatus==='ENABLED'?'正常':'已禁用'}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="在线状态"><el-tag :type="detail.online?'success':'info'" size="small">{{detail.online?'在线':'离线'}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="注册时间">{{detail.createTime?new Date(detail.createTime).toLocaleString():'-'}}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{detail.updateTime?new Date(detail.updateTime).toLocaleString():'-'}}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.role && detail.role!=='ROLE_ADMIN'" style="margin-top:20px">
          <h4>重置密码</h4>
          <div style="display:flex;gap:8px">
            <el-input v-model="resetPwd" type="password" show-password placeholder="新密码（至少 8 位）" style="width:280px"/>
            <el-button type="primary" @click="submitReset">重置密码</el-button>
          </div>
          <p style="font-size:12px;color:#909399;margin-top:8px">重置后该用户旧 Token 全局失效，需重新登录。</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import authApi from '../api/auth'
export default {
  data() { return { records: [], total: 0, keyword: '', page: 1, loading: false, detailVisible: false, detailLoading: false, detail: {}, resetPwd: '' } },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        const params = { page: this.page, size: 10 }
        if (this.keyword.trim()) params.keyword = this.keyword.trim()
        const r = await authApi.adminUsers(params)
        this.records = (r.data && r.data.records) || []
        this.total = (r.data && r.data.total) || 0
      } catch (e) { /* 拦截器已提示 */ }
      finally { this.loading = false }
    },
    search() { this.page = 1; this.load() },
    reset() { this.keyword = ''; this.page = 1; this.load() },
    async openDetail(row) {
      this.detailVisible = true; this.detailLoading = true; this.detail = {}; this.resetPwd = ''
      try {
        const res = await authApi.adminUserDetail(row.id)
        this.detail = res.data || {}
      } catch (e) { /* 拦截器已提示 */ }
      finally { this.detailLoading = false }
    },
    async submitReset() {
      if (!this.resetPwd || this.resetPwd.length < 8) return this.$message.error('新密码至少 8 位')
      try {
        await authApi.resetPassword({ userId: this.detail.id, newPassword: this.resetPwd })
        this.$message.success('密码已重置，该用户旧 Token 已失效，登录锁定已解除')
        this.resetPwd = ''
      } catch (e) { this.$message.error('重置失败') }
    },
    async toggleStatus(row) {
      const target = row.userStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      const doSet = async () => {
        try {
          await authApi.setUserStatus(row.id, target)
          this.$message.success(target === 'DISABLED' ? '已禁用，该用户已被强制下线' : '已启用')
          this.load()
        } catch (e) { /* 拦截器已提示 */ }
      }
      if (target === 'DISABLED') {
        try { await this.$confirm('禁用后该用户将被强制下线且无法登录，确认操作？', '禁用用户 - ' + row.username, { type: 'warning' }); doSet() } catch (e) { /* 用户取消 */ }
      } else { doSet() }
    }
  }
}
</script>
