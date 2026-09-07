import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Divider, Drawer, Form, Input, InputNumber, message, Modal, Select, Space, Tabs, Table, Tag, Typography } from 'antd'
import paymentConfigApi from '@/api/paymentConfig'

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
  sortOrder: 0
})

export default function PaymentConfig() {
  const [activeTab, setActiveTab] = useState('channel')
  const [channelList, setChannelList] = useState([])
  const [appList, setAppList] = useState([])
  const [channelDialogVisible, setChannelDialogVisible] = useState(false)
  const [appDialogVisible, setAppDialogVisible] = useState(false)
  const [channelForm] = Form.useForm()
  const [appForm] = Form.useForm()
  const [editingChannel, setEditingChannel] = useState(null)
  const [editingApp, setEditingApp] = useState(null)

  const loadChannels = useCallback(() => {
    paymentConfigApi.listAllChannels().then((response) => {
      setChannelList(response?.data || [])
    })
  }, [])

  const loadApps = useCallback(() => {
    paymentConfigApi.listAllApps().then((response) => {
      setAppList(response?.data || [])
    })
  }, [])

  useEffect(() => {
    loadChannels()
    loadApps()
  }, [loadChannels, loadApps])

  const openChannelDialog = (row) => {
    setEditingChannel(row || null)
    if (row) {
      channelForm.setFieldsValue({ ...row })
    } else {
      channelForm.setFieldsValue(defaultChannelForm())
    }
    setChannelDialogVisible(true)
  }

  const submitChannel = () => {
    channelForm.validateFields().then((values) => {
      if (values.configParams) {
        try {
          JSON.parse(values.configParams)
        } catch {
          message.error('渠道参数JSON格式错误')
          return
        }
      }

      const request = editingChannel
        ? paymentConfigApi.updateChannel(editingChannel.id, values)
        : paymentConfigApi.createChannel(values)

      request.then((response) => {
        message.success(response.message || '保存成功')
        setChannelDialogVisible(false)
        loadChannels()
        loadApps()
      })
    })
  }

  const toggleChannelStatus = (row) => {
    const targetStatus = row.channelStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    paymentConfigApi.updateChannelStatus(row.id, targetStatus).then((response) => {
      message.success(response.message || '状态修改成功')
      loadChannels()
      loadApps()
    })
  }

  const deleteChannel = (row) => {
    Modal.confirm({
      title: '确认删除该支付渠道？',
      content: '若已有应用关联，数据库会拒绝删除。',
      okText: '确认',
      cancelText: '取消',
      onOk: () => {
        paymentConfigApi.deleteChannel(row.id).then((response) => {
          message.success(response.message || '删除成功')
          loadChannels()
          loadApps()
        })
      }
    })
  }

  const openAppDialog = (row) => {
    setEditingApp(row || null)
    if (row) {
      appForm.setFieldsValue({ ...row })
    } else {
      appForm.setFieldsValue(defaultAppForm())
    }
    setAppDialogVisible(true)
  }

  const submitApp = () => {
    appForm.validateFields().then((values) => {
      const request = editingApp
        ? paymentConfigApi.updateApp(editingApp.id, values)
        : paymentConfigApi.createApp(values)

      request.then((response) => {
        message.success(response.message || '保存成功')
        setAppDialogVisible(false)
        loadApps()
      })
    })
  }

  const toggleAppStatus = (row) => {
    const targetStatus = row.appStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    paymentConfigApi.updateAppStatus(row.id, targetStatus).then((response) => {
      message.success(response.message || '状态修改成功')
      loadApps()
    })
  }

  const deleteApp = (row) => {
    Modal.confirm({
      title: '确认删除该支付应用？',
      content: '历史订单仍会保留支付应用ID。',
      okText: '确认',
      cancelText: '取消',
      onOk: () => {
        paymentConfigApi.deleteApp(row.id).then((response) => {
          message.success(response.message || '删除成功')
          loadApps()
        })
      }
    })
  }

  const reloadConfig = () => {
    paymentConfigApi.reload().then((response) => {
      message.success(response.message || '配置重新加载成功')
    })
  }

  const channelColumns = [
    { title: '#', width: 50, render: (_, __, index) => index + 1 },
    { title: '渠道名称', dataIndex: 'channelName', width: 140 },
    { title: '渠道编码', dataIndex: 'channelCode', width: 120 },
    {
      title: '状态',
      dataIndex: 'channelStatus',
      width: 110,
      render: (status) => (
        <Tag color={status === 'ENABLED' ? 'success' : 'default'}>{status}</Tag>
      )
    },
    { title: '描述', dataIndex: 'channelDesc' },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '操作',
      width: 260,
      align: 'center',
      render: (_, row) => (
        <Space size="small">
          <Button type="link" onClick={() => openChannelDialog(row)}>编辑</Button>
          <Button type="link" onClick={() => toggleChannelStatus(row)}>
            {row.channelStatus === 'ENABLED' ? '禁用' : '启用'}
          </Button>
          <Button type="link" danger onClick={() => deleteChannel(row)}>删除</Button>
        </Space>
      )
    }
  ]

  const appColumns = [
    { title: '#', width: 50, render: (_, __, index) => index + 1 },
    { title: '应用名称', dataIndex: 'appName', width: 170 },
    { title: '应用编码', dataIndex: 'appCode', width: 190 },
    { title: '所属渠道', dataIndex: 'channelName', width: 120 },
    {
      title: '状态',
      dataIndex: 'appStatus',
      width: 110,
      render: (status) => (
        <Tag color={status === 'ENABLED' ? 'success' : 'default'}>{status}</Tag>
      )
    },
    { title: '描述', dataIndex: 'appDesc' },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '操作',
      width: 260,
      align: 'center',
      render: (_, row) => (
        <Space size="small">
          <Button type="link" onClick={() => openAppDialog(row)}>编辑</Button>
          <Button type="link" onClick={() => toggleAppStatus(row)}>
            {row.appStatus === 'ENABLED' ? '禁用' : '启用'}
          </Button>
          <Button type="link" danger onClick={() => deleteApp(row)}>删除</Button>
        </Space>
      )
    }
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>支付配置</Typography.Title>

      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          type="card"
          items={[
            {
              key: 'channel',
              label: '支付渠道配置',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => openChannelDialog()}>新增渠道</Button>
                    <Button onClick={loadChannels}>刷新</Button>
                    <Button type="primary" onClick={reloadConfig}>重新加载配置缓存</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    dataSource={channelList}
                    columns={channelColumns}
                    bordered
                    pagination={false}
                  />
                </>
              )
            },
            {
              key: 'app',
              label: '支付应用配置',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => openAppDialog()}>新增应用</Button>
                    <Button onClick={loadApps}>刷新</Button>
                    <Button type="primary" onClick={reloadConfig}>重新加载配置缓存</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    dataSource={appList}
                    columns={appColumns}
                    bordered
                    pagination={false}
                  />
                </>
              )
            }
          ]}
        />
      </Card>

      <Drawer
        title={editingChannel ? '编辑支付渠道' : '新增支付渠道'}
        open={channelDialogVisible}
        width={560}
        onClose={() => setChannelDialogVisible(false)}
        footer={<div style={{ textAlign: 'right' }}>
          <Button style={{ marginRight: 8 }} onClick={() => setChannelDialogVisible(false)}>取消</Button>
          <Button type="primary" onClick={submitChannel}>保存</Button>
        </div>}
      >
        <Form form={channelForm} layout="vertical">
          <Form.Item label="渠道名称" name="channelName" rules={[{ required: true, message: '请输入渠道名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="渠道编码" name="channelCode" rules={[{ required: true, message: '请输入渠道编码' }]}>
            <Input placeholder="例如：WXPAY、ALIPAY" />
          </Form.Item>
          <Form.Item label="状态" name="channelStatus" rules={[{ required: true, message: '请选择状态' }]}>
            <Select>
              <Select.Option value="ENABLED">启用</Select.Option>
              <Select.Option value="DISABLED">禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="排序" name="sortOrder" initialValue={0}>
            <InputNumber min={0} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="描述" name="channelDesc">
            <Input />
          </Form.Item>
          <Divider plain orientation="left">微信商户信息</Divider>
          <Form.Item label="微信 AppID" name="appid">
            <Input placeholder="例如：wx74862e0dfcf69954" />
          </Form.Item>
          <Form.Item label="微信商户号" name="mchId">
            <Input placeholder="例如：1558950191" />
          </Form.Item>
          <Form.Item label="商户 API 证书序列号" name="mchSerialNo">
            <Input />
          </Form.Item>
          <Form.Item label="商户私钥（apiclient_key.pem 内容）" name="privateKey">
            <Input.TextArea rows={6} placeholder={'-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----'} />
          </Form.Item>
          <Form.Item label="APIv3 密钥" name="apiV3Key">
            <Input />
          </Form.Item>
          <Form.Item label="APIv2 密钥（partnerKey）" name="partnerKey">
            <Input />
          </Form.Item>
          <Divider plain orientation="left">支付宝商户信息</Divider>
          <Form.Item label="支付宝应用 ID" name="alipayAppId">
            <Input placeholder="例如：9021000136667568" />
          </Form.Item>
          <Form.Item label="卖家 PID（seller_id）" name="sellerId">
            <Input />
          </Form.Item>
          <Form.Item label="应用私钥（base64）" name="merchantPrivateKey">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item label="支付宝公钥（base64）" name="alipayPublicKey">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item label="渠道参数JSON" name="configParams">
            <Input.TextArea rows={8} placeholder='例如：{"domain":"https://api.mch.weixin.qq.com"}' />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={editingApp ? '编辑支付应用' : '新增支付应用'}
        open={appDialogVisible}
        width={640}
        onClose={() => setAppDialogVisible(false)}
        footer={<div style={{ textAlign: 'right' }}>
          <Button style={{ marginRight: 8 }} onClick={() => setAppDialogVisible(false)}>取消</Button>
          <Button type="primary" onClick={submitApp}>保存</Button>
        </div>}
      >
        <Form form={appForm} layout="vertical">
          <Form.Item label="应用名称" name="appName" rules={[{ required: true, message: '请输入应用名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="应用编码" name="appCode" rules={[{ required: true, message: '请输入应用编码' }]}>
            <Input placeholder="例如：WXPAY_DEFAULT" />
          </Form.Item>
          <Form.Item label="所属渠道" name="channelId" rules={[{ required: true, message: '请选择支付渠道' }]}>
            <Select placeholder="请选择支付渠道">
              {channelList.map((channel) => (
                <Select.Option key={channel.id} value={channel.id}>
                  {channel.channelName}（{channel.channelCode}）
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="状态" name="appStatus" rules={[{ required: true, message: '请选择状态' }]}>
            <Select>
              <Select.Option value="ENABLED">启用</Select.Option>
              <Select.Option value="DISABLED">禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="排序" name="sortOrder" initialValue={0}>
            <InputNumber min={0} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="描述" name="appDesc">
            <Input />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="商户参数（appid/商户号/密钥等）统一在「支付渠道配置」中维护"
            style={{ marginBottom: 8 }}
          />
        </Form>
      </Drawer>
    </div>
  )
}
