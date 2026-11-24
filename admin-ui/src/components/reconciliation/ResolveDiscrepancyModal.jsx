import { useEffect, useState } from 'react'
import { Input, Modal, Tag } from 'antd'

const { TextArea } = Input

// 备注输入状态收敛在弹窗内部，避免每次击键重渲染整张对账页。
export default function ResolveDiscrepancyModal({ open, discrepancy, confirmLoading, onClose, onSubmit }) {
  const [remark, setRemark] = useState('')

  useEffect(() => {
    if (open) setRemark('')
  }, [open])

  return (
    <Modal
      title="标记差异已处理"
      open={open}
      width={500}
      onCancel={onClose}
      onOk={() => onSubmit(remark)}
      confirmLoading={confirmLoading}
      okText="确认处理"
      cancelText="取消"
    >
      {discrepancy && (
        <div style={{ marginBottom: 12 }}>
          <p>
            差异类型：<Tag color="red">{discrepancy.discrepancyTypeText}</Tag>
            业务单号：<strong>{discrepancy.bizNo}</strong>
          </p>
        </div>
      )}
      <TextArea
        rows={4}
        value={remark}
        onChange={(e) => setRemark(e.target.value)}
        placeholder="请输入差异处理说明（必填），如：已与渠道核对为跨日清算，差异关闭"
        maxLength={500}
      />
    </Modal>
  )
}
