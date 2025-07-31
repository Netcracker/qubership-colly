import {UserInfo} from "../entities/users";
import {QuickFilter, QuickFilterClear, QuickFilterControl, Toolbar, ToolbarButton} from "@mui/x-data-grid";
import {Box, InputAdornment, TextField, Tooltip} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import SearchIcon from "@mui/icons-material/Search";
import CancelIcon from "@mui/icons-material/Cancel";

interface ClusterTableToolbarProps {
    userInfo: UserInfo;
    isEditEnabled: boolean;
    onEditClick: () => void;
}

export default function ClusterTableToolbar({userInfo, isEditEnabled, onEditClick}: ClusterTableToolbarProps) {
    return (
        <Toolbar>
            <Box sx={{display: 'flex', justifyContent: 'space-between', width: '100%'}}>
                <QuickFilter>
                    <QuickFilterControl
                        render={({ref, ...controlProps}, state) => (
                            <TextField
                                {...controlProps}
                                inputRef={ref}
                                aria-label="Search"
                                placeholder="Search..."
                                size="small"
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
                <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                    {userInfo.authenticated && userInfo.isAdmin && (
                        <Tooltip title="Edit">
                            <ToolbarButton
                                size="medium"
                                onClick={onEditClick}
                                disabled={isEditEnabled}
                            >
                                <EditIcon fontSize="small"/>
                            </ToolbarButton>
                        </Tooltip>
                    )}
                </Box>
            </Box>

        </Toolbar>)
}
