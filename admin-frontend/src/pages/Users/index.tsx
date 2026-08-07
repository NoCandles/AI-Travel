import React, { useEffect, useRef, useState } from 'react';
import { Button, Input, Select, Space, Table, Tag, Modal, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { userService } from '../../services/users';

const UsersPage: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>();
  const [detail, setDetail] = useState<any>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const keywordRef = useRef('');

  const fetchData = () => {
    setLoading(true);
    userService.list({ page, size: 20, keyword: keywordRef.current, status: statusFilter }).then((res: any) => {
      setData(res.data.records);
      setTotal(res.data.total);
    }).finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, [page, statusFilter]);

  const handleSearch = () => {
    keywordRef.current = keyword;
    setPage(1);
    fetchData();
  };

  const handleBan = (id: string, status: number) => {
    Modal.confirm({
      title: status === 0 ? '确认封禁此用户？' : '确认解封此用户？',
      onOk: () => userService.updateStatus(id, status).then(() => {
        message.success(status === 0 ? '已封禁' : '已解封');
        fetchData();
      }),
    });
  };

  const handleDetail = (id: string) => {
    userService.detail(id).then((res: any) => {
      setDetail(res.data);
      setDetailOpen(true);
    });
  };

  const columns: ColumnsType<any> = [
    { title: '用户ID', dataIndex: 'id', width: 120, ellipsis: true },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '手机号', dataIndex: 'phone', width: 140 },
    { title: '城市', dataIndex: 'city', width: 100 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (v: number) => v === 1 ? <Tag color="green">正常</Tag> : <Tag color="red">封禁</Tag>,
    },
    { title: '注册时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作', key: 'action', width: 160,
      render: (_, record: any) => (
        <Space>
          <Button size="small" onClick={() => handleDetail(record.id)}>详情</Button>
          {record.status === 1 ? (
            <Button size="small" danger onClick={() => handleBan(record.id, 0)}>封禁</Button>
          ) : (
            <Button size="small" onClick={() => handleBan(record.id, 1)}>解封</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>用户管理</h2>
      <Space style={{ marginBottom: 16 }}>
        <Input placeholder="搜索昵称/手机号" value={keyword} onChange={(e) => setKeyword(e.target.value)} style={{ width: 200 }} />
        <Select allowClear placeholder="状态" style={{ width: 100 }} value={statusFilter} onChange={setStatusFilter}>
          <Select.Option value={1}>正常</Select.Option>
          <Select.Option value={0}>封禁</Select.Option>
        </Select>
        <Button type="primary" onClick={handleSearch}>搜索</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage }} scroll={{ x: 900 }} />
      <Modal open={detailOpen} title="用户详情" footer={null} onCancel={() => setDetailOpen(false)} width={600}>
        {detail && (
          <div>
            <p><b>ID:</b> {detail.id}</p>
            <p><b>昵称:</b> {detail.nickname}</p>
            <p><b>手机:</b> {detail.phone}</p>
            <p><b>城市:</b> {detail.city}</p>
            <p><b>行程数:</b> {detail.tripCount} | <b>发布数:</b> {detail.publishCount}</p>
            <p><b>积分:</b> {detail.points} | <b>等级:</b> {detail.level}</p>
            <p><b>关注:</b> {detail.followingCount} | <b>粉丝:</b> {detail.followerCount}</p>
            <p><b>注册:</b> {detail.createdAt}</p>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default UsersPage;
