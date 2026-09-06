<template>
  <div>
    <h2 class="adm-page-title">支付配置</h2>

    <div class="adm-card">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="支付渠道配置" name="channel">
          <div class="toolbar">
            <el-button type="primary" @click="openChannelDialog()">新增渠道</el-button>
            <el-button @click="loadChannels">刷新</el-button>
            <el-button type="primary" plain @click="reloadConfig">重新加载配置缓存</el-button>
          </div>
          <el-table :data="channelList" border style="width: 100%">
            <el-table-column type="index" width="50"></el-table-column>
            <el-table-column prop="channelName" label="渠道名称" width="140"></el-table-column>
            <el-table-column prop="channelCode" label="渠道编码" width="120"></el-table-column>
            <el-table-column label="状态" width="110">
              <template slot-scope="scope">
                <el-tag :type="scope.row.channelStatus === 'ENABLED' ? 'success' : 'info'">
                  {{ scope.row.channelStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="channelDesc" label="描述"></el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80"></el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template slot-scope="scope">
                <el-button type="text" @click="openChannelDialog(scope.row)">编辑</el-button>
                <el-button type="text" @click="toggleChannelStatus(scope.row)">
                  {{ scope.row.channelStatus === 'ENABLED' ? '禁用' : '启用' }}
                </el-button>
                <el-button type="text" class="danger-btn" @click="deleteChannel(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="支付应用配置" name="app">
          <div class="toolbar">
            <el-button type="primary" @click="openAppDialog()">新增应用</el-button>
            <el-button @click="loadApps">刷新</el-button>
            <el-button type="primary" plain @click="reloadConfig">重新加载配置缓存</el-button>
          </div>
          <el-table :data="appList" border style="width: 100%">
            <el-table-column type="index" width="50"></el-table-column>
            <el-table-column prop="appName" label="应用名称" width="170"></el-table-column>
            <el-table-column prop="appCode" label="应用编码" width="190"></el-table-column>
            <el-table-column prop="channelName" label="所属渠道" width="120"></el-table-column>
            <el-table-column label="状态" width="110">
              <template slot-scope="scope">
                <el-tag :type="scope.row.appStatus === 'ENABLED' ? 'success' : 'info'">
                  {{ scope.row.appStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="appDesc" label="描述"></el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80"></el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template slot-scope="scope">
                <el-button type="text" @click="openAppDialog(scope.row)">编辑</el-button>
                <el-button type="text" @click="toggleAppStatus(scope.row)">
                  {{ scope.row.appStatus === 'ENABLED' ? '禁用' : '启用' }}
                </el-button>
                <el-button type="text" class="danger-btn" @click="deleteApp(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer :title="channelForm.id ? '编辑支付渠道' : '新增支付渠道'" :visible.sync="channelDialogVisible" size="560px" direction="rtl">
      <el-form ref="channelForm" :model="channelForm" :rules="channelRules" label-position="top" style="padding:0 8px">
        <el-form-item label="渠道名称" prop="channelName">
          <el-input v-model="channelForm.channelName"></el-input>
        </el-form-item>
        <el-form-item label="渠道编码" prop="channelCode">
          <el-input v-model="channelForm.channelCode" placeholder="例如：WXPAY、ALIPAY"></el-input>
        </el-form-item>
        <el-form-item label="状态" prop="channelStatus">
          <el-select v-model="channelForm.channelStatus" style="width: 100%">
            <el-option label="启用" value="ENABLED"></el-option>
            <el-option label="禁用" value="DISABLED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="channelForm.sortOrder" :min="0" :step="1"></el-input-number>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="channelForm.channelDesc"></el-input>
        </el-form-item>
        <el-form-item label="渠道参数JSON">
          <el-input
            v-model="channelForm.configParams"
            type="textarea"
            :rows="8"
            placeholder='例如：{"domain":"https://api.mch.weixin.qq.com"}'></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" style="text-align:right">
        <el-button @click="channelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitChannel">保存</el-button>
      </div>
    </el-drawer>

    <el-drawer :title="appForm.id ? '编辑支付应用' : '新增支付应用'" :visible.sync="appDialogVisible" size="640px" direction="rtl">
      <el-form ref="appForm" :model="appForm" :rules="appRules" label-position="top" style="padding:0 8px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="appForm.appName"></el-input>
        </el-form-item>
        <el-form-item label="应用编码" prop="appCode">
          <el-input v-model="appForm.appCode" placeholder="例如：WXPAY_DEFAULT"></el-input>
        </el-form-item>
        <el-form-item label="所属渠道" prop="channelId">
          <el-select v-model="appForm.channelId" style="width: 100%" placeholder="请选择支付渠道">
            <el-option
              v-for="channel in channelList"
              :key="channel.id"
              :label="channel.channelName + '（' + channel.channelCode + '）'"
              :value="channel.id">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="appStatus">
          <el-select v-model="appForm.appStatus" style="width: 100%">
            <el-option label="启用" value="ENABLED"></el-option>
            <el-option label="禁用" value="DISABLED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="appForm.sortOrder" :min="0" :step="1"></el-input-number>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="appForm.appDesc"></el-input>
        </el-form-item>
        <el-form-item label="应用参数JSON">
          <el-input
            v-model="appForm.appConfig"
            type="textarea"
            :rows="12"
            placeholder='微信示例：{"appid":"...","mchId":"...","apiV3Key":"...","notifyUrl":"..."}'></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" style="text-align:right">
        <el-button @click="appDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApp">保存</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import paymentConfigApi from '../api/paymentConfig'

const defaultChannelForm = () => ({
  id: null,
  channelName: '',
  channelCode: '',
  channelStatus: 'ENABLED',
  channelDesc: '',
  configParams: '',
  sortOrder: 0
})

const defaultAppForm = () => ({
  id: null,
  appName: '',
  appCode: '',
  appStatus: 'ENABLED',
  channelId: null,
  appDesc: '',
  appConfig: '',
  sortOrder: 0
})

export default {
  data() {
    return {
      activeTab: 'channel',
      channelList: [],
      appList: [],
      channelDialogVisible: false,
      appDialogVisible: false,
      channelForm: defaultChannelForm(),
      appForm: defaultAppForm(),
      channelRules: {
        channelName: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
        channelCode: [{ required: true, message: '请输入渠道编码', trigger: 'blur' }],
        channelStatus: [{ required: true, message: '请选择状态', trigger: 'change' }]
      },
      appRules: {
        appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
        appCode: [{ required: true, message: '请输入应用编码', trigger: 'blur' }],
        channelId: [{ required: true, message: '请选择支付渠道', trigger: 'change' }],
        appStatus: [{ required: true, message: '请选择状态', trigger: 'change' }]
      }
    }
  },

  created() {
    this.loadChannels()
    this.loadApps()
  },

  methods: {
    loadChannels() {
      paymentConfigApi.listAllChannels().then(response => {
        this.channelList = response.data || []
      })
    },

    loadApps() {
      paymentConfigApi.listAllApps().then(response => {
        this.appList = response.data || []
      })
    },

    openChannelDialog(row) {
      this.channelForm = row ? Object.assign(defaultChannelForm(), row) : defaultChannelForm()
      this.channelDialogVisible = true
      this.$nextTick(() => this.$refs.channelForm && this.$refs.channelForm.clearValidate())
    },

    submitChannel() {
      this.$refs.channelForm.validate(valid => {
        if (!valid || !this.validateJson(this.channelForm.configParams)) {
          return
        }
        const request = this.channelForm.id
          ? paymentConfigApi.updateChannel(this.channelForm.id, this.channelForm)
          : paymentConfigApi.createChannel(this.channelForm)
        request.then(response => {
          this.$message.success(response.message || '保存成功')
          this.channelDialogVisible = false
          this.loadChannels()
          this.loadApps()
        })
      })
    },

    toggleChannelStatus(row) {
      const targetStatus = row.channelStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      paymentConfigApi.updateChannelStatus(row.id, targetStatus).then(response => {
        this.$message.success(response.message || '状态修改成功')
        this.loadChannels()
        this.loadApps()
      })
    },

    deleteChannel(row) {
      this.$confirm('确认删除该支付渠道？若已有应用关联，数据库会拒绝删除。', '提示', { type: 'warning' })
        .then(() => paymentConfigApi.deleteChannel(row.id))
        .then(response => {
          this.$message.success(response.message || '删除成功')
          this.loadChannels()
          this.loadApps()
        })
        .catch(() => {})
    },

    openAppDialog(row) {
      this.appForm = row ? Object.assign(defaultAppForm(), row) : defaultAppForm()
      this.appDialogVisible = true
      this.$nextTick(() => this.$refs.appForm && this.$refs.appForm.clearValidate())
    },

    submitApp() {
      this.$refs.appForm.validate(valid => {
        if (!valid || !this.validateJson(this.appForm.appConfig)) {
          return
        }
        const request = this.appForm.id
          ? paymentConfigApi.updateApp(this.appForm.id, this.appForm)
          : paymentConfigApi.createApp(this.appForm)
        request.then(response => {
          this.$message.success(response.message || '保存成功')
          this.appDialogVisible = false
          this.loadApps()
        })
      })
    },

    toggleAppStatus(row) {
      const targetStatus = row.appStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      paymentConfigApi.updateAppStatus(row.id, targetStatus).then(response => {
        this.$message.success(response.message || '状态修改成功')
        this.loadApps()
      })
    },

    deleteApp(row) {
      this.$confirm('确认删除该支付应用？历史订单仍会保留支付应用ID。', '提示', { type: 'warning' })
        .then(() => paymentConfigApi.deleteApp(row.id))
        .then(response => {
          this.$message.success(response.message || '删除成功')
          this.loadApps()
        })
        .catch(() => {})
    },

    reloadConfig() {
      paymentConfigApi.reload().then(response => {
        this.$message.success(response.message || '支付配置已重新加载')
      })
    },

    validateJson(value) {
      if (!value || !value.trim()) {
        return true
      }
      try {
        const parsed = JSON.parse(value)
        if (Object.prototype.toString.call(parsed) !== '[object Object]') {
          this.$message.error('配置参数必须是 JSON 对象')
          return false
        }
        return true
      } catch (e) {
        this.$message.error('JSON 格式不正确：' + e.message)
        return false
      }
    }
  }
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.danger-btn {
  color: #f56c6c;
}
</style>
