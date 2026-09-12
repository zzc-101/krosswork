import { useState } from 'react';
import { ApiOutlined, CheckCircleFilled, DownloadOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { App, Avatar, Button, Card, Col, Input, Modal, Popconfirm, Row, Space, Tag, Typography } from 'antd';
import { AdminApiClient } from '../../../apiClient';
import type { IntegrationCatalogItem } from '../../../contracts';
import { Page } from '../../../components/Page';
import { RefreshButton } from '../../../components/RefreshButton';
import { ResourceState } from '../../../components/ResourceState';
import { useResource } from '../../../hooks/useResource';

export function OrganizationIntegrationsPage({ api }: { api: AdminApiClient }) {
  const state = useResource(() => api.organizationIntegrations(), [api]);
  const { message } = App.useApp();
  const [hostTarget, setHostTarget] = useState<IntegrationCatalogItem>();
  const [hostDraft, setHostDraft] = useState('gitlab.com');
  const [savingHost, setSavingHost] = useState(false);

  const install = async (item: IntegrationCatalogItem, host?: string) => {
    await api.installIntegration(item.catalogId, host ? { host } : {});
    message.success(`${item.name} 已启用，成员可在工作台用自己的账号连接`);
    await state.reload();
  };
  const uninstall = async (item: IntegrationCatalogItem) => {
    if (!item.installationId) return;
    await api.uninstallIntegration(item.installationId);
    message.success(`${item.name} 已关闭，成员授权一并撤销`);
    await state.reload();
  };
  const openHost = (item: IntegrationCatalogItem) => {
    setHostTarget(item);
    setHostDraft(item.host || 'gitlab.com');
  };
  const saveHost = async () => {
    if (!hostTarget) return;
    setSavingHost(true);
    try {
      if (hostTarget.installed && hostTarget.installationId) {
        await api.patchIntegration(hostTarget.installationId, { host: hostDraft.trim() });
        message.success('实例地址已更新，成员需要重新连接');
      } else {
        await install(hostTarget, hostDraft.trim());
      }
      setHostTarget(undefined);
      await state.reload();
    } finally {
      setSavingHost(false);
    }
  };

  return (
    <Page
      title="工作连接器"
      subtitle="为组织打开官方 MCP。成员各自用自己的账号授权，Agent 才能访问对应系统。"
      action={<RefreshButton onClick={state.reload} />}
    >
      <ResourceState state={state} empty="还没有可安装的工作连接器。">
        {(items) => (
          <Row gutter={[18, 18]}>
            {items.map((item) => (
              <Col key={item.catalogId} xs={24} lg={12} xl={8}>
                <Card className={item.installed ? 'skill-market-card installed' : 'skill-market-card'}>
                  <div className="skill-market-head">
                    <Avatar shape="square" size={44} icon={<ApiOutlined />} />
                    <div>
                      <Space>
                        <Typography.Title level={4}>{item.name}</Typography.Title>
                        {item.installed && item.status === 'active' && (
                          <CheckCircleFilled className="skill-installed-icon" />
                        )}
                      </Space>
                      <Typography.Text type="secondary">
                        {item.host ? `${item.catalogId} · ${item.host}` : item.catalogId}
                      </Typography.Text>
                    </div>
                  </div>
                  <Typography.Paragraph className="skill-market-description" ellipsis={{ rows: 3 }}>
                    {item.description || '暂无说明'}
                  </Typography.Paragraph>
                  <Space wrap>
                    {item.installed ? <Tag>已启用</Tag> : <Tag>未启用</Tag>}
                    {!item.installable && <Tag>即将支持</Tag>}
                  </Space>
                  <div className="skill-market-actions">
                    {item.installed && item.installationId ? (
                      <Space>
                        {item.hostRequired && (
                          <Button icon={<EditOutlined />} onClick={() => openHost(item)}>更改地址</Button>
                        )}
                        <Popconfirm
                          title={`关闭 ${item.name}？`}
                          description="组织内所有成员的授权都会被撤销。"
                          okText="关闭"
                          cancelText="取消"
                          onConfirm={() => uninstall(item)}
                        >
                          <Button danger icon={<DeleteOutlined />}>关闭</Button>
                        </Popconfirm>
                      </Space>
                    ) : (
                      <Button
                        type="primary"
                        icon={<DownloadOutlined />}
                        disabled={!item.installable}
                        onClick={() => {
                          if (item.hostRequired) {
                            openHost(item);
                            return;
                          }
                          void install(item);
                        }}
                      >
                        启用
                      </Button>
                    )}
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </ResourceState>
      <Modal
        title={hostTarget?.installed ? `更改 ${hostTarget?.name ?? ''} 地址` : `启用 ${hostTarget?.name ?? ''}`}
        open={Boolean(hostTarget)}
        okText={hostTarget?.installed ? '保存' : '启用'}
        confirmLoading={savingHost}
        onCancel={() => setHostTarget(undefined)}
        onOk={() => void saveHost()}
      >
        <Typography.Paragraph type="secondary">
          GitLab.com 填 gitlab.com。自建实例填主机名，不要带路径。更改地址后成员需要重新授权。
        </Typography.Paragraph>
        <Input
          value={hostDraft}
          placeholder="gitlab.com"
          onChange={(event) => setHostDraft(event.target.value)}
        />
      </Modal>
    </Page>
  );
}
