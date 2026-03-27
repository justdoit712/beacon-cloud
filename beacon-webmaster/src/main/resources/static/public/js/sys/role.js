$(function () {
    var option = {
        url: '../sys/role/list',
        pagination: true,
        sidePagination: 'server',
        toolbar: '#toolbar',
        queryParams: function (params) {
            return {
                limit: params.limit,
                offset: params.offset,
                name: $("#role-name").val(),
                status: $("#role-status").val()
            };
        },
        striped: true,
        columns: [
            {checkbox: true},
            {
                field: 'id',
                title: '序号',
                width: 40,
                formatter: function (value, row, index) {
                    var pageSize = $('#table').bootstrapTable('getOptions').pageSize;
                    var pageNumber = $('#table').bootstrapTable('getOptions').pageNumber;
                    return pageSize * (pageNumber - 1) + index + 1;
                }
            },
            {title: '用户ID', field: 'id'},
            {field: 'name', title: '名称'},
            {field: 'remark', title: '备注'},
            {
                title: '状态', field: 'status', formatter: function (value) {
                    return value === 1
                        ? '<span class="label label-success">有效</span>'
                        : '<span class="label label-danger">无效</span>';
                }
            }
        ]
    };
    $('#table').bootstrapTable(option);
});

var ztree;

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        role: {}
    },
    methods: {
        getSelectedRoleId: function () {
            var selectedRow = getSelectedRow();
            if (!selectedRow || selectedRow.id == null) {
                return null;
            }
            return selectedRow.id;
        },
        del: function () {
            var rows = $("#table").bootstrapTable("getSelections");
            if (rows == null || rows.length == 0) {
                alert('请选择您要删除的行');
                return;
            }

            layer.confirm('您确定要删除所选数据吗？', {
                btn: ['确定', '取消']
            }, function () {
                var ids = [];
                $.each(rows, function (i, row) {
                    ids.push(row.id);
                });

                $.ajax({
                    type: "POST",
                    url: "../sys/role/del",
                    data: JSON.stringify(ids),
                    success: function (r) {
                        if (r.code === 0) {
                            layer.alert('删除成功');
                            $('#table').bootstrapTable('refresh');
                        } else {
                            layer.alert(r.msg);
                        }
                    },
                    error: function () {
                        layer.alert('服务器没有返回数据，可能服务器忙，请重试');
                    }
                });
            });
        },
        add: function () {
            vm.showList = false;
            vm.title = "新增";
            vm.role = {};
        },
        update: function () {
            var roleId = vm.getSelectedRoleId();
            if (roleId == null) {
                return;
            }
            $.get("../sys/role/info/" + roleId, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.role = (r && r.data) ? r.data.role : {};
            });
        },
        saveOrUpdate: function () {
            var url = vm.role.id == null ? "../sys/role/save" : "../sys/role/update";
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.role),
                success: function (r) {
                    if (r.code === 0) {
                        layer.alert(r.msg, function (index) {
                            layer.close(index);
                            vm.reload();
                        });
                    } else {
                        layer.alert(r.msg);
                    }
                }
            });
        },
        reload: function () {
            vm.showList = true;
            $("#table").bootstrapTable('refresh');
        },
        menuTree: function () {
            var roleId = vm.getSelectedRoleId();
            if (roleId == null) {
                return;
            }
            vm.getMenu(roleId);
            layer.open({
                type: 1,
                offset: '50px',
                skin: 'layui-layer-molv',
                title: "选择菜单",
                area: ['300px', '450px'],
                shade: 0,
                shadeClose: false,
                content: jQuery("#menuLayer"),
                btn: ['确定', '取消'],
                btn1: function (index) {
                    var treeData = ztree.getCheckedNodes(true);
                    var menuIds = "";
                    for (var i = 0; i < treeData.length; i++) {
                        menuIds += "&menuIds=" + treeData[i].id;
                    }
                    $.ajax({
                        method: "post",
                        url: "../sys/role/menu/assign",
                        data: "roleId=" + roleId + menuIds,
                        success: function (r) {
                            if (r.code === 0) {
                                layer.alert('分配成功');
                                layer.close(index);
                            } else {
                                layer.alert(r.msg);
                            }
                        },
                        error: function () {
                            layer.alert('服务器没有返回数据，可能服务器忙，请重试');
                        }
                    });
                }
            });
        },
        getMenu: function (roleId) {
            if (roleId == null) {
                roleId = vm.getSelectedRoleId();
                if (roleId == null) {
                    return;
                }
            }
            var setting = {
                data: {
                    simpleData: {
                        enable: true,
                        idKey: "id",
                        pIdKey: "parentId",
                        rootPId: -1
                    },
                    key: {
                        url: "nourl"
                    }
                },
                check: {
                    enable: true,
                    chkStyle: "checkbox",
                    chkboxType: {
                        "Y": "ps",
                        "N": "ps"
                    }
                }
            };
            $.get("../sys/role/menu/" + roleId, function (roleMenuResp) {
                var roleMenu = (roleMenuResp && roleMenuResp.code === 0 && $.isArray(roleMenuResp.data))
                    ? roleMenuResp.data : [];
                $.get("../sys/role/menu/tree", function (r) {
                    var menuList = (r && r.data && $.isArray(r.data.menuList)) ? r.data.menuList : [];
                    ztree = $.fn.zTree.init($("#menuTree"), setting, menuList);
                    ztree.expandAll(true);
                    for (var i = 0; i < roleMenu.length; i++) {
                        var node = ztree.getNodeByParam("id", roleMenu[i]);
                        if (node) {
                            node.checked = true;
                            ztree.updateNode(node);
                        }
                    }
                });
            });
        }
    }
});
