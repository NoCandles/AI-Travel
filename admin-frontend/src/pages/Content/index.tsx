import React, { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Table, Tag, Tabs, Modal, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { contentService } from '../../services/content';

const ContentPage: React.FC = () => {
  const [publishData, setPublishData] = useState<any[]>([]);
  const [publishLoading, setPublishLoading] = useState(false);
  const [pubPage, setPubPage] = useState(1);
  const [pubTotal, setPubTotal] = useState(0);
  const [pubKeyword, setPubKeyword] = useState('');
  const [pubStatus, setPubStatus] = useState<number | undefined>();

  const [commentData, setCommentData] = useState<any[]>([]);
  const [commentLoading, setCommentLoading] = useState(false);
  const [comPage, setComPage] = useState(1);
  const [comTotal, setComTotal] = useState(0);
  const [comKeyword, setComKeyword] = useState('');
  const [activeTab, setActiveTab] = useState('publish');

  // ====== 发布列表 ======
  const fetchPublish = () => {
    setPublishLoading(true);
    contentService.listPublish({ page: pubPage, size: 20, keyword: pubKeyword, reviewStatus: pubStatus })
      .then((res: any) => { setPublishData(res.data.records); setPubTotal(res.data.total); })
      .finally(() => setPublishLoading(false));
  };

  // ====== 评论列表 ======
  const fetchComments = () => {
    setCommentLoading(true);
    contentService.listComments({ page: comPage, size: 20, keyword: comKeyword })
      .then((res: any) => { setCommentData(res.data.records); setComTotal(res.data.total); })
      .finally(() => setCommentLoading(false));
  };

  useEffect(() => { fetchPublish(); }, [pubPage, pubStatus]);
  useEffect(() => { fetchComments(); }, [comPage]);

  const handlePublishAction = (id: string, status: number) => {
    Modal.confirm({
      title: status === 0 ? '确认下架？' : '确认恢复？',
      onOk: () => contentService.updatePublishStatus(id, status).then(() => { message.success('操作成功'); fetchPublish(); }),
    });
  };

  const handleDeletePublish = (id: string) => {
    Modal.confirm({
      title: '确认删除此发布？将同时删除关联评论',
      okType: 'danger',
      onOk: () => contentService.deletePublish(id).then(() => { message.success('已删除'); fetchPublish(); }),
    });
  };

  const handleDeleteComment = (id: number) => {
    Modal.confirm({
      title: '确认删除此评论？',
      onOk: () => contentService.deleteComment(id).then(() => { message.success('已删除'); fetchComments(); }),
    });
  };

  const pubColumns: ColumnsType<any> = [
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '用户ID', dataIndex: 'userId', width: 100, ellipsis: true },
    { title: '浏览', dataIndex: 'viewCount', width: 60 },
    { title: '赞', dataIndex: 'likeCount', width: 50 },
    { title: '评', dataIndex: 'commentCount', width: 50 },
    {
      title: '审核状态', dataIndex: 'reviewStatus', width: 100,
      render: (v: number) => {
        if (v === 0) return <Tag color="red">已下架</Tag>;
        if (v === 2) return <Tag color="orange">待审核</Tag>;
        return <Tag color="green">正常</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作', key: 'action', width: 180,
      render: (_, r: any) => (
        <Space>
          {r.reviewStatus !== 0 ? (
            <Button size="small" danger onClick={() => handlePublishAction(r.id, 0)}>下架</Button>
          ) : (
            <Button size="small" onClick={() => handlePublishAction(r.id, 1)}>恢复</Button>
          )}
          <Button size="small" danger onClick={() => handleDeletePublish(r.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  const comColumns: ColumnsType<any> = [
    { title: '内容', dataIndex: 'content', width: 300, ellipsis: true },
    { title: '用户ID', dataIndex: 'userId', width: 100, ellipsis: true },
    { title: '发布ID', dataIndex: 'publishId', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, r: any) => (
        <Button size="small" danger onClick={() => handleDeleteComment(r.id)}>删除</Button>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>内容审核</h2>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'publish', label: `广场发布`,
          children: (
            <>
              <Space style={{ marginBottom: 16 }}>
                <Input placeholder="搜索标题" value={pubKeyword} onChange={(e) => setPubKeyword(e.target.value)} style={{ width: 200 }} />
                <Select allowClear placeholder="审核状态" style={{ width: 120 }} value={pubStatus} onChange={setPubStatus}>
                  <Select.Option value={1}>正常</Select.Option>
                  <Select.Option value={0}>已下架</Select.Option>
                  <Select.Option value={2}>待审核</Select.Option>
                </Select>
                <Button type="primary" onClick={() => { setPubPage(1); fetchPublish(); }}>搜索</Button>
              </Space>
              <Table columns={pubColumns} dataSource={publishData} rowKey="id" loading={publishLoading}
                pagination={{ current: pubPage, total: pubTotal, pageSize: 20, onChange: setPubPage }} scroll={{ x: 1000 }} />
            </>
          ),
        },
        {
          key: 'comments', label: `评论管理`,
          children: (
            <>
              <Space style={{ marginBottom: 16 }}>
                <Input placeholder="搜索内容" value={comKeyword} onChange={(e) => setComKeyword(e.target.value)} style={{ width: 200 }} />
                <Button type="primary" onClick={() => { setComPage(1); fetchComments(); }}>搜索</Button>
              </Space>
              <Table columns={comColumns} dataSource={commentData} rowKey="id" loading={commentLoading}
                pagination={{ current: comPage, total: comTotal, pageSize: 20, onChange: setComPage }} scroll={{ x: 800 }} />
            </>
          ),
        },
      ]} />
    </div>
  );
};

export default ContentPage;
