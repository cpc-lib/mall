<template>
  <div>
    <h2 class="adm-page-title">密码重置申请</h2>
    <div class="adm-card" style="padding:8px 20px 16px">
    <el-table :data="resetRequests" size="small" style="width:100%">
      <el-table-column prop="id" label="ID" width="60"/>
      <el-table-column label="用户名" width="140"><template slot-scope="s"><a @click="openDetail(s.row)" style="color:#409eff;cursor:pointer">{{s.row.username}}</a></template></el-table-column>
      <el-table-column prop="remark" label="申请说明" min-width="160"><template slot-scope="s">{{s.row.remark||'-'}}</template></el-table-column>
      <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag :type="s.row.status==='PENDING'?'warning':s.row.status==='HANDLED'?'success':'info'" size="mini">{{s.row.status==='PENDING'?'待处理':s.row.status==='HANDLED'?'已处理':'已拒绝'}}</el-tag></template></el-table-column>
      <el-table-column label="申请时间" width="170"><template slot-scope="s">{{s.row.createTime ? new Date(s.row.createTime).toLocaleString() : '-'}}</template></el-table-column>
      <el-table-column label="处理时间" width="170"><template slot-scope="s">{{s.row.updateTime ? new Date(s.row.updateTime).toLocaleString() : '-'}}</template></el-table-column>
      <el-table-column label="操作" width="90">
        <template slot-scope="s">
          <el-button size="mini" @click="openDetail(s.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-drawer :title="detail ? '重置申请详情 - ' + detail.username : '重置申请详情'" :visible.sync="detailVisible" size="560px" direction="rtl">
      <div v-if="detail" style="padding:0 8px">
        <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="ID">{{detail.id}}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{detail.username}}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detail.status==='PENDING'?'warning':detail.status==='HANDLED'?'success':'info'" size="small">{{detail.status==='PENDING'?'待处理':detail.status==='HANDLED'?'已处理':'已拒绝'}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="申请说明">{{detail.remark || '-'}}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{detail.createTime ? new Date(detail.createTime).toLocaleString() : '-'}}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{detail.updateTime ? new Date(detail.updateTime).toLocaleString() : '-'}}</el-descriptions-item>
          <el-descriptions-item label="处理备注">{{detail.adminRemark || '-'}}</el-descriptions-item>
        </el-descriptions>
        <template v-if="detail.status==='PENDING'">
          <el-form label-position="top">
            <el-form-item label="受理备注（可选）">
              <el-input v-model="handleRemark" maxlength="255" type="textarea" :rows="2" placeholder="管理员备注"/>
            </el-form-item>
            <el-form-item label="拒绝原因（可选，点击“确认拒绝”时生效）">
              <el-input v-model="rejectRemark" maxlength="255" type="textarea" :rows="2" placeholder="拒绝原因"/>
            </el-form-item>
          </el-form>
        </template>
      </div>
      <div slot="footer" v-if="detail" style="text-align:right">
        <template v-if="detail.status==='PENDING'">
          <el-button type="danger" @click="rejectResetRequest">确认拒绝</el-button>
          <el-button type="primary" @click="handleResetRequest">受理并生成随机密码</el-button>
        </template>
        <el-button v-else @click="detailVisible=false">关闭</el-button>
      </div>
    </el-drawer>

    <el-dialog title="密码重置成功" :visible.sync="prResultVisible" width="420px">
      <el-input :value="generatedPassword" readonly style="margin-bottom:12px"/>
      <p style="color:#f50;margin:0">新密码仅此一次展示，请立即复制并告知用户；系统仅保存密码哈希，关闭后无法再次查看。</p>
      <span slot="footer"><el-button type="primary" @click="copyGeneratedPassword">复制</el-button><el-button @click="prResultVisible=false">我已保存</el-button></span>
    </el-dialog>
  </div>
</template>
<script>
import authApi from '../api/auth'
export default {
  data() { return { resetRequests: [], detail: null, detailVisible: false, handleRemark: '', rejectRemark: '', prResultVisible: false, generatedPassword: '' } },
  created() { this.load() },
  methods: {
    async load() {
      try {
        const r = await authApi.passwordResetRequests()
        const list = r.data || []
        this.resetRequests = list
        if (this.detail) this.detail = list.find(x => x.id === this.detail.id) || this.detail
      } catch (e) { this.resetRequests = [] }
    },
    openDetail(row) { this.detail = row; this.detailVisible = true; this.handleRemark = ''; this.rejectRemark = '' },
    async handleResetRequest() {
      const r = await authApi.handlePasswordResetRequest(this.detail.id, (this.handleRemark || '').trim())
      this.detailVisible = false
      this.generatedPassword = (r.data && r.data.newPassword) || ''
      this.prResultVisible = true
      await this.load()
    },
    async rejectResetRequest() {
      await authApi.rejectPasswordResetRequest(this.detail.id, (this.rejectRemark || '').trim())
      this.$message.success('已拒绝该申请')
      this.detailVisible = false
      await this.load()
    },
    copyGeneratedPassword() {
      try {
        const input = document.createElement('textarea')
        input.value = this.generatedPassword
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        document.body.removeChild(input)
        this.$message.success('已复制')
      } catch (e) { this.$message.warning('复制失败，请手动复制') }
    }
  }
}
</script>
