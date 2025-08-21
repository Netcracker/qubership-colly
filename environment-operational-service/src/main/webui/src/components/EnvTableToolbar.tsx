import React from "react";
import {Badge, Box, Checkbox, FormControlLabel, InputAdornment, TextField, Tooltip} from "@mui/material";
import ViewColumnIcon from '@mui/icons-material/ViewColumn';
import FilterListIcon from '@mui/icons-material/FilterList';
import CancelIcon from '@mui/icons-material/Cancel';
import SearchIcon from '@mui/icons-material/Search';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    ColumnsPanelTrigger,
    FilterPanelTrigger,
    QuickFilter,
    QuickFilterClear,
    QuickFilterControl,
    Toolbar,
    ToolbarButton
} from '@mui/x-data-grid';
import {Environment} from "../entities/environments";
import {UserInfo} from "../entities/users";

interface EnvTableToolbarProps {
    userInfo: UserInfo;
    selectedEnvironment: Environment | null;
    showAllNamespaces: boolean;
    onShowEditDialog: () => void;
    onShowConfirmPopup: () => void;
    onShowAllNamespacesChange: (checked: boolean) => void;
}

export default function EnvTableToolbar({
    userInfo,
    selectedEnvironment,
    showAllNamespaces,
    onShowEditDialog,
    onShowConfirmPopup,
    onShowAllNamespacesChange
}: EnvTableToolbarProps) {
    return (
        <Toolbar>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                <QuickFilter>
                    <QuickFilterControl
                        render={({ref, ...controlProps}, state) => (
                            <TextField
                                {...controlProps}
                                inputRef={ref}
                                aria-label="Search"
                                placeholder="Search..."
                                size="small"
                                sx={{ width: 250, minWidth: 250 }}
                                slotProps={{
                                    input: {
                                        startAdornment: (
                                            <InputAdornment position="start">
                                                <SearchIcon fontSize="small"/>
                                            </InputAdornment>
                                        ),
                                        endAdornment: state.value ? (
                                            <InputAdornment position="end">
                                                <QuickFilterClear
                                                    edge="end"
                                                    size="small"
                                                    aria-label="Clear search"
                                                >
                                                    <CancelIcon fontSize="small"/>
                                                </QuickFilterClear>
                                            </InputAdornment>
                                        ) : null,
                                        ...controlProps.slotProps?.input,
                                    },
                                    ...controlProps.slotProps,
                                }}
                            />
                        )}
                    />
                </QuickFilter>

                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {userInfo.authenticated && userInfo.isAdmin && (
                        <Tooltip title="Edit">
                            <ToolbarButton
                                size="medium"
                                onClick={onShowEditDialog}
                                disabled={!selectedEnvironment}>
                                <EditIcon fontSize="small"/>
                            </ToolbarButton>
                        </Tooltip>
                    )}
                    {userInfo.authenticated && userInfo.isAdmin && (
                        <Tooltip title="Delete">
                            <ToolbarButton
                                size="medium"
                                onClick={onShowConfirmPopup}
                                disabled={!selectedEnvironment}>
                                <DeleteIcon fontSize="small"/>
                            </ToolbarButton>
                        </Tooltip>
                    )}

                    <Tooltip title="Columns">
                        <ColumnsPanelTrigger render={<ToolbarButton/>}>
                            <ViewColumnIcon fontSize="small"/>
                        </ColumnsPanelTrigger>
                    </Tooltip>

                    <Tooltip title="Filters">
                        <FilterPanelTrigger
                            render={(props, state) => (
                                <ToolbarButton {...props} color="default">
                                    <Badge badgeContent={state.filterCount} color="primary" variant="dot">
                                        <FilterListIcon fontSize="small"/>
                                    </Badge>
                                </ToolbarButton>
                            )}
                        />
                    </Tooltip>
                    <Tooltip title="Show/Hide Unexisting Namespaces">
                        <FormControlLabel
                            control={<Checkbox
                                checked={showAllNamespaces}
                                onChange={(event) => {
                                    onShowAllNamespacesChange(event.target.checked);
                                }}/>}
                            label={"Show All Namespaces"}
                        />
                    </Tooltip>
                </Box>
            </Box>
        </Toolbar>
    );
}