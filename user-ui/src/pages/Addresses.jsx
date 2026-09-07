import { useEffect, useState } from 'react'
import { Button, Cascader, Empty, Form, Input, Modal, Popconfirm, Switch, message } from 'antd'
import addressApi from '@/api/address'
import request from '@/utils/request'

export default function Addresses() {
  const [list, setList] = useState([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form] = Form.useForm()
  const [regions, setRegions] = useState([])

  useEffect(() => {
    request.get('/api/regions/tree').then(r => setRegions(r.data || [])).catch(() => {})
  }, [])

  const load = async () => {
    setLoading(true)
    try {
      const r = await addressApi.list()
      setList(r.data || [])
    } finally { setLoading(false) }
  }
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const openNew = () => { setEditing(null); form.resetFields(); form.setFieldsValue({ isDefault: list.length === 0 }); setOpen(true) }
  const openEdit = (a) => {
    setEditing(a)
    form.setFieldsValue({
      receiverName: a.receiverName, receiverPhone: a.receiverPhone, detail: a.detail,
      region: [a.provinceCode, a.cityCode, a.districtCode],
      isDefault: a.isDefault === '1'
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const [province, city, district] = v.region || []
    const payload = {
      receiverName: v.receiverName, receiverPhone: v.receiverPhone, detail: v.detail,
      provinceCode: province || '', cityCode: city || '', districtCode: district || '',
      province: regions.find(p => p.value === province)?.label || province || '',
      city: regions.find(p => p.value === province)?.children?.find(c => c.value === city)?.label || city || '',
      district: regions.find(p => p.value === province)?.children?.find(c => c.value === city)?.children?.find(d => d.value === district)?.label || district || '',
      isDefault: v.isDefault ? '1' : '0'
    }
    if (editing) { await addressApi.update(editing.id, payload); message.success('已更新') }
    else { await addressApi.create(payload); message.success('已新增') }
    setOpen(false); load()
  }

  const del = async (id) => { await addressApi.remove(id); message.success('已删除'); load() }
  const setDefault = async (id) => { await addressApi.setDefault(id); load() }

  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">收货地址</h2>
      <Button type="primary" onClick={openNew} style={{ marginBottom: 12 }}>+ 新增地址</Button>
      {list.length === 0
        ? <div className="tb-cardbox"><Empty description="暂无收货地址，先新增一个吧" /></div>
        : <div className="addr-list">
            {list.map(a => (
              <div key={a.id} className={`addr-item${a.isDefault === '1' ? ' default' : ''}`}>
                <div className="addr-top">
                  <span className="addr-name">{a.receiverName}</span>
                  <span className="addr-phone">{a.receiverPhone}</span>
                  {a.isDefault === '1' && <span className="addr-tag">默认</span>}
                </div>
                <div className="addr-addr">{a.province}{a.city}{a.district}{a.detail}</div>
                <div className="addr-ops">
                  {a.isDefault !== '1' && <Button type="link" size="small" onClick={() => setDefault(a.id)}>设为默认</Button>}
                  <Button type="link" size="small" onClick={() => openEdit(a)}>编辑</Button>
                  <Popconfirm title="确定删除该地址？" onConfirm={() => del(a.id)}><Button type="link" size="small" danger>删除</Button></Popconfirm>
                </div>
              </div>
            ))}
          </div>
      }
      <Modal open={open} onCancel={() => setOpen(false)} title={editing ? '编辑地址' : '新增地址'} onOk={submit}>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="receiverName" label="收货人" rules={[{ required: true, message: '请输入收货人姓名' }]}><Input maxLength={32} placeholder="姓名" /></Form.Item>
          <Form.Item name="receiverPhone" label="电话" rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}><Input maxLength={32} placeholder="手机号" /></Form.Item>
          <Form.Item name="region" label="所在地区" rules={[{ required: true, message: '请选择省/市/区' }]}><Cascader options={regions} placeholder="省 / 市 / 区" changeOnSelect /></Form.Item>
          <Form.Item name="detail" label="详细地址" rules={[{ required: true, message: '请输入详细地址' }]}><Input.TextArea maxLength={128} rows={2} placeholder="街道、门牌号等" /></Form.Item>
          <Form.Item name="isDefault" label="设为默认" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
    </div>
  </div>
}
