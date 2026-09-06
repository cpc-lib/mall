import { Button, DatePicker, Form, message } from 'antd'

import billApi from '@/api/bill'

function triggerDownload(url, filename) {
  const element = document.createElement('a')
  element.setAttribute('href', url)
  element.setAttribute('download', filename)
  element.style.display = 'none'
  document.body.appendChild(element)
  element.click()
  document.body.removeChild(element)
}

export default function Download() {
  const [wxForm] = Form.useForm()
  const [aliForm] = Form.useForm()

  const downloadBill = (type) => {
    const billDate = wxForm.getFieldValue('billDate')?.format('YYYY-MM-DD')
    if (!billDate) {
      message.warning('请选择微信账单日期')
      return
    }

    billApi.downloadBillWxPay(billDate, type).then((response) => {
      const content = response?.data?.result || ''
      triggerDownload(
        `data:application/vnd.ms-excel;charset=utf-8,${encodeURIComponent(content)}`,
        `${billDate}-${type}`
      )
    })
  }

  const downloadBillAliPay = (type) => {
    const billDate = aliForm.getFieldValue('billDate')?.format('YYYY-MM-DD')
    if (!billDate) {
      message.warning('请选择支付宝账单日期')
      return
    }

    billApi.downloadBillAliPay(billDate, type).then((response) => {
      const downloadUrl = response?.data?.downloadUrl
      if (!downloadUrl) {
        message.error('未获取到账单下载地址')
        return
      }
      triggerDownload(downloadUrl, `${billDate}-${type}`)
    })
  }

  return (
    <div>
      <h2 className="adm-page-title">下载账单</h2>

      <div className="adm-card">
        <div className="adm-card-head"><span className="adm-card-title">微信账单申请</span></div>
        <Form form={wxForm} layout="inline">
          <Form.Item name="billDate">
            <DatePicker placeholder="选择账单日期" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => downloadBill('tradebill')}>下载交易账单</Button>
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => downloadBill('fundflowbill')}>下载资金账单</Button>
          </Form.Item>
        </Form>
      </div>

      <div className="adm-card">
        <div className="adm-card-head"><span className="adm-card-title">支付宝账单申请</span></div>
        <Form form={aliForm} layout="inline">
          <Form.Item name="billDate">
            <DatePicker placeholder="选择账单日期" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => downloadBillAliPay('trade')}>下载交易账单</Button>
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => downloadBillAliPay('signcustomer')}>下载资金账单</Button>
          </Form.Item>
        </Form>
      </div>
    </div>
  )
}
