import { Alert, Button, Card, Col, Drawer, Row, Statistic, Table, Typography } from 'antd'

export default function ReconcileSummaryDrawer({ open, onClose, currentImport, loading, summary, rows, columns, fen }) {
  return (
    <Drawer
      title={`账账核对汇总${currentImport ? ` - ${currentImport.importNo}（${currentImport.billDate}）` : ''}`}
      open={open}
      width={900}
      onClose={onClose}
      footer={<div style={{ textAlign: 'right' }}><Button onClick={onClose}>关闭</Button></div>}
    >
      <Card loading={loading} bordered={false}>
        {summary && <>
          <Alert
            type={summary.balanced ? 'success' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            message={summary.balanced ? '账账平衡' : '账账不平衡，请处理差异'}
            description={`净差额（渠道-平台）：${fen(summary.netDifference)}；待处理差异：${summary.openDiscrepancyCount || 0} 笔。账单种类 ${summary.billKind || '-'}，渠道 ${summary.channelCode || '-'}。`}
          />
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col span={8}><Card size="small"><Statistic title="渠道净额" value={Number(summary.channelNetAmount || 0) / 100} precision={2} prefix="¥" /></Card></Col>
            <Col span={8}><Card size="small"><Statistic title="平台净额" value={Number(summary.localNetAmount || 0) / 100} precision={2} prefix="¥" /></Card></Col>
            <Col span={8}><Card size="small"><Statistic title="待处理差异" value={summary.openDiscrepancyCount || 0} suffix="笔" /></Card></Col>
          </Row>
          <Table rowKey="key" dataSource={rows} columns={columns} pagination={false} bordered size="middle" />
          <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
            平账条件：支付笔数/金额一致、退款成功笔数/金额一致、净额一致，并且不存在 OPEN 差异单。当前净额口径暂不包含渠道手续费；手续费属于后续资金账单核对范围。
          </Typography.Paragraph>
        </>}
      </Card>
    </Drawer>
  )
}
