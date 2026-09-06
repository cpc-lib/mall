<template>
  <div>
    <h2 class="adm-page-title">重置用户密码</h2>
    <div class="adm-card">
      <div style="margin-bottom:16px">
        <el-input v-model="keyword" placeholder="用户名关键字" clearable size="small" style="width:220px;margin-right:8px" @keyup.enter.native="search" @clear="search"/>
        <el-button size="small" type="primary" @click="search">查询</el-button>
        <el-button size="small" @click="resetFilter">重置</el-button>
      </div>
      <el-table :data="records" v-loading="loading" size="small" style="width:100%">
        <el-table-column width="55" align="center">
          <template slot-scope="s">
            <el-radio v-model="selectedId" :label="s.row.id" :disabled="s.row.role==='ROLE_ADMIN'"><span></span></el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="username" label="用户名" width="160"/>
        <el-table-column label="角色" width="120"><template slot-scope="s"><el-tag :type="s.row.role==='ROLE_ADMIN'?'danger':'primary'" size="mini">{{s.row.role}}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag :type="s.row.userStatus==='ENABLED'?'success':'danger'" size="mini">{{s.row.userStatus==='ENABLED'?'正常':'已禁用'}}</el-tag></template></el-table-column>
        <el-table-column label="注册时间" width="180"><template slot-scope="s">{{s.row.createTime?new Date(s.row.createTime).toLocaleString():'-'}}</template></el-table-column>
        <el-table-column label="说明" min-width="170">
          <template slot-scope="s">
            <span v-if="s.row.role==='ROLE_ADMIN'" style="color:#909399;font-size:12px">管理员账号不可重置密码</span>
            <span v-else-if="selectedId===s.row.id" style="color:#67c23a;font-size:12px">已选择该用户</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:16px;text-align:right" background layout="total, prev, pager, next"
        :current-page.sync="page" :page-size="10" :total="total" @current-change="load"/>
    </div>
    <div class="adm-card" style="margin-top:16px;max-width:720px">
      <div style="margin-bottom:12px">
        已选用户：
        <template v-if="selectedUser"><strong>{{selectedUser.username}}</strong>（ID: {{selectedUser.id}}）</template>
        <span v-else style="color:#909399">请先在上方列表中勾选一个普通用户</span>
      </div>
      <div style="display:flex;gap:12px;align-items:center">
        <el-input v-model="newPassword" type="password" show-password placeholder="新密码（至少8位）" style="width:260px" :disabled="!selectedUser"/>
        <el-button type="primary" :disabled="!selectedUser || newPassword.length<8" @click="submitReset">重置密码</el-button>
      </div>
      <p style="margin:12px 0 0;font-size:12px;color:#8C8C8C">重置后目标用户旧 Token 将全局失效，需重新登录。</p>
    </div>
  </div>
</template>
<script>
import authApi from '../api/auth'
export default {
  data() { return { records: [], total: 0, keyword: '', page: 1, loading: false, selectedId: null, selectedUser: null, newPassword: '' } },
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
    resetFilter() { this.keyword = ''; this.page = 1; this.load() },
    async submitReset() {
      if (!this.selectedUser || this.newPassword.length < 8) return
      try {
        await authApi.resetPassword({ userId: this.selectedUser.id, newPassword: this.newPassword })
        this.$message.success('密码已重置，目标用户旧 Token 已全局失效')
        this.newPassword = ''
      } catch (e) { /* 拦截器已提示 */ }
    }
  },
  watch: {
    selectedId(val) {
      if (val == null) { this.selectedUser = null; return }
      this.selectedUser = this.records.find(u => u.id === val) || this.selectedUser
    }
  }
}
</script>
