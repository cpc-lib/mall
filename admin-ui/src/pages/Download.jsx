import { Button, Card, DatePicker, Form, message, Typography } from 'antd'
import * as XLSX from 'xlsx'

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
      if (!content) {
        message.error('下载失败：微信返回的账单内容为空')
        return
      }
      try {
        // 微信账单文本通常带 UTF-8 BOM；先剥离，避免 BOM 被固化进 XLSX 首列表头单元格。
        // 再由 SheetJS 把文本账单真正转换为 OOXML XLSX，而不是只改文件扩展名。
        const normalizedContent = content.charCodeAt(0) === 0xfeff ? content.slice(1) : content
        const workbook = XLSX.read(normalizedContent, { type: 'string', raw: true })
        XLSX.writeFile(workbook, `${billDate}-${type}.xlsx`, { bookType: 'xlsx' })
      } catch (error) {
        message.error(`生成 XLSX 账单失败：${error?.message || '未知错误'}`)
      }
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
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>下载账单</Typography.Title>

      <Card title="微信账单申请">
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
      </Card>

      <Card title="支付宝账单申请">
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
      </Card>
    </div>
  )
}
